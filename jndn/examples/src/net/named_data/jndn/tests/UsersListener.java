package net.named_data.jndn.tests;
import net.named_data.jndn.*;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.SigningInfo;
import net.named_data.jndn.security.pib.PibImpl;
import net.named_data.jndn.security.tpm.TpmBackEnd;
import org.json.*;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.util.Blob;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class UsersListener {

    private static Set<String> users = new HashSet<>();
    private static KeyChain keyChain = newKeyChain();
    private static OnInterestCallback onModifyUsers = (name, interest, face, l, interestFilter) -> {
        String lastElement  = interest.getName().get(-1).getValue().toString();
        if(lastElement.equals("users")){
            return;
        }
        sendAck(face, interest.getName());
        users.add(lastElement);

        for (String userName : users){
            try {
                face.expressInterest(new Name(userName + "/users"), (interest1, data1) -> {});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private static void sendAck(Face face, Name name){
        Data data = new Data(name);
        data.setContent(new Blob("ok"));
        send(face, data);
    }

    private static OnInterestCallback onInterestCallback =
        (name, interest, face, number, interestFilter) -> createAndSend(face, name);

    private static void createAndSend(Face face, Name name){
        Data data = newUsersData(name);
        withBlobType(data);
        withSignedContent(data);
        send(face, data);
    }

    private static void send(Face face, Data data){
        try {
            face.putData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Data newUsersData(Name name){
        Data data = new Data(name);
        data.setContent(getContent());
        return data;
    }

    private static void withBlobType(Data data){
        MetaInfo mi = new MetaInfo();
        mi.setType(ContentType.BLOB);
        data.setMetaInfo(mi);
    }

    private static void withSignedContent(Data data){
        SigningInfo signingInfo = new SigningInfo(SigningInfo.SignerType.SHA256);
        try {
            keyChain.sign(data, signingInfo);
        } catch (TpmBackEnd.Error | KeyChain.Error | PibImpl.Error error) {
            error.printStackTrace();
        }
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

    private static Blob getContent(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("users", users);
        return new Blob(jsonObject.toString());
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) throws IOException, EncodingException, SecurityException {
        Face face = new Face("192.168.0.107");
        face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName() );
        Name name = new Name("/users");
        System.out.println("Working at /users");
        face.registerPrefix(name, onInterestCallback, name1 -> System.out.println("Fail"));
        face.setInterestFilter(name, onModifyUsers);
        while(true) {
            face.processEvents();
        }
    }
}
