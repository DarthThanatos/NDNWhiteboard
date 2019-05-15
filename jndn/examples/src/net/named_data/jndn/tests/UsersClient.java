package net.named_data.jndn.tests;

import net.named_data.jndn.*;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.pib.PibImpl;
import net.named_data.jndn.util.Blob;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UsersClient {

    private static ArrayList<String> chatHistory = new ArrayList<>();
    private static String userName = "Default_name";
    private static Set<String> users = new HashSet<>();

    private static OnInterestCallback onUserInterest = (name, interest, face, l, interestFilter) -> {
        String lastElement  = interest.getName().get(-1).getValue().toString();
        if(lastElement.equals(userName)){
            //this is a request to send data associated with prefix /{username}, another callback handles it
            return;
        }
        if(lastElement.equals("users")){
            // ping signalizing that the register of users changed -> pull changes
            sendAck(face, name);
            pullUsers(face);
            return;
        }
        //otherwise it is a ping signalizing that the history of a user with userName==lastElement has changed -> pull that history
        sendAck(face, name);
        pullUserHistory(face, lastElement);
    };

    private static void sendAck(Face face, Name name){
        Data data = new Data(name);
        data.setContent(new Blob("ok"));
        send(face, data);
    }

    private static void send(Face face, Data data){
        try {
            face.putData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void pull(Face face, String prefix, OnData onData){
        try {
            face.expressInterest(new Name(prefix), onData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void pullUserHistory(Face face, String userName){
        pull(face, userName, (interest, data) -> {
            System.out.println("history of a user changed, my master");
            System.out.println(data.getContent().toString());
        });
    }

    private static void pullUsers(Face face){
        pull(face, "/users", (interest, data) -> {
            JSONObject users = new JSONObject(data.getContent().toString());
            String[] usersArr = users.getJSONArray("users")
                    .join(",").split(",");
            UsersClient.users.addAll(
                    Arrays.stream(usersArr)
                        .map(s -> s.replace("\"", ""))
                        .collect(Collectors.toList())
                    );
        });
    }

    private static OnInterestCallback fetchUserHistory = (name, interest, face, l, interestFilter) -> {
        Data data = new Data(name);
        data.setContent(getContent());
        try {
            face.putData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    private static Blob getContent(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(userName, chatHistory);
        return new Blob(jsonObject.toString());
    }

    private static OnTimeout onTimeout = interest -> System.out.println("Timeout for " + interest.getName());

    private static OnRegisterFailed onRegisterFailed = name -> System.out.println("Failed to register " + name);

    private static String prompt(String promptTxt, boolean prepareRes, String defaultTxt){
        System.out.println(promptTxt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            String line = reader.readLine();
            return prepareRes ? line.trim().replaceAll("\\s+", "_") : line;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return defaultTxt;
    }

    private static String promptUserName(){
        return prompt(
            "Enter your user name:",
            true,
            "Default_name"
        );
    }

    private static String promptChatMsg(){
        return prompt(
          "Enter some chat msg:",
          false,
          "Error getting msg"
        );
    }

    private static KeyChain newKeyChain(){
        KeyChain keyChain = null;
        try {
            keyChain = new KeyChain();
        } catch (SecurityException | KeyChain.Error | PibImpl.Error | IOException e) {
            e.printStackTrace();
        }
        return keyChain;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) throws IOException, SecurityException {
        Face face = new Face("192.168.0.107");
        KeyChain keyChain = newKeyChain();
        face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName() );
        userName = promptUserName();
        Name name = new Name("/users");
        name.append(userName);
        System.out.println(String.format("expressing interest in the prefix: %s", name.toString()));

        face.registerPrefix(new Name(userName), fetchUserHistory, onRegisterFailed);
        face.setInterestFilter(new Name(userName), onUserInterest);
        face.expressInterest(name, (interest, data) -> {}, onTimeout);

        Thread t = new Thread(() -> {
            while(true) {
                try {
                    face.processEvents();
                } catch (IOException | EncodingException e) {
                    e.printStackTrace();
                }
            }
        });
        t.setDaemon(true);
        t.start();
        System.out.println("Welcome " + userName);
        while(true) {
            chatHistory.add(promptChatMsg());
            for (String user: users){
                if(!user.equals(userName)){
                    //pinging  all users that this client's history changed
                    face.expressInterest(new Name(user + "/" + userName), (interest, data) -> {});
                }
            }
        }

    }
}