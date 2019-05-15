package edu.ucla.cs.ndnwhiteboard.tasks;

import android.os.AsyncTask;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.ucla.cs.ndnwhiteboard.WhiteboardActivity;
import edu.ucla.cs.ndnwhiteboard.custom_views.DrawingView;

public class WhiteboardListener{

    private static String userName = "Default_name";
    private static Set<String> users = new HashSet<>();

    private WhiteboardActivity whiteboardActivity;
    private String TAG = PingTask.class.getSimpleName();  // TAG for logging

    private String jsonString = "";

    public WhiteboardListener(WhiteboardActivity whiteboardActivity){
        this.whiteboardActivity = whiteboardActivity;
        userName = whiteboardActivity.username;
    }

    private OnInterestCallback onUserInterest = (name, interest, face, l, interestFilter) -> {
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

    private void sendAck(Face face, Name name){
        Data data = new Data(name);
        data.setContent(new Blob("ok"));
        send(face, data);
    }

    private void send(Face face, Data data){
        try {
            face.putData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pull(Face face, String prefix, OnData onData){
        try {
            face.expressInterest(new Name(prefix), onData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pullUserHistory(Face face, String userName){
        pull(face, userName, (interest, data) -> {
            Log.e(TAG, "history of a user changed, my master");
            System.out.println(data.getContent().toString());
            whiteboardActivity.runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    whiteboardActivity.drawingView_canvas.callback(data.getContent().toString());

                }
            });
        });
    }

    private void pullUsers(Face face){
        pull(face, "/users", (interest, data) -> {
            JSONObject usersJson = null;
            try {
                Log.e(TAG, "pulling users");
                Log.e(TAG, data.getContent().toString());
                usersJson = new JSONObject(data.getContent().toString());
                String[] usersArr = usersJson.getJSONArray("users")
                        .join(",").split(",");
                List<String> correctedNames = new ArrayList<>();
                for (String userName :usersArr){
                    correctedNames.add(userName.replace("\"", ""));
                }
                users.addAll(correctedNames);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private OnInterestCallback fetchUserHistory = (name, interest, face, l, interestFilter) -> {
        Data data = new Data(name);
        data.setContent(new Blob(jsonString));
        try {
            face.putData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    private OnTimeout onTimeout = interest -> System.out.println("Timeout for " + interest.getName());

    private OnRegisterFailed onRegisterFailed = name -> System.out.println("Failed to register " + name);

    void execute() {
        new Thread(() -> {
            Name name = new Name("/users");
            name.append(userName);
            Log.e(TAG, String.format("expressing interest in the prefix: %s", name.toString()));
            Face face = whiteboardActivity.m_face;
            try {
                face.registerPrefix(new Name(userName), fetchUserHistory, onRegisterFailed);
                face.setInterestFilter(new Name(userName), onUserInterest);
                face.expressInterest(name, (interest, data) -> {}, onTimeout);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void informUsers(String jsonString){
        this.jsonString = jsonString;
        Log.e(TAG, "informing users");
        new Thread(() -> {
            for (String user: users){
                if(!user.equals(userName)){
                    //pinging  all users that this client's history changed
                    try {
                        Log.e(TAG, "pinging " + user);
                        whiteboardActivity.m_face.expressInterest(new Name(user + "/" + userName), (interest, data) -> {});
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

}
