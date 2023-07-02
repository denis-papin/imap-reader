package lu.isd.imap_reader;


import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import lu.isd.imap_reader.config.Account;
import lu.isd.imap_reader.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class ReadMail {
    private static final Logger logger = LoggerFactory.getLogger(ReadMail.class);

    private final List<String> imapInFolder;
    private final List<String> imapOutFolder;

    // { "<9EFCCC6B-29F3-412A-971B-202BC7824F0A@isd.lu>", "..." }
    private static List<String> indexes = new ArrayList<>();

    // the initial index from the idx file which have disappeared from disk.
    private List<String> inRecovery = new ArrayList<>();

    private final Map<String, String> contactMap = new HashMap<>();

    private Session session;

    private static Path baseFolder;

    private final String hostname;
    private final String login;
    private final String password;
    private final Integer port;
    private final Boolean group;
    private final Boolean recover;

    private final String subFolder;

    public enum Direction {
        IN,
        OUT
    }


    public ReadMail(Config config, Account account) {
        baseFolder = Paths.get(config.emailFolder);
        //subFolder = account.name+" ("+account.login+")";
        subFolder = account.name;
        group = account.group;
        recover = account.recover;
        hostname = account.server;
        login = account.login;
        password = account.password;
        port = account.port;
        imapInFolder = account.imapInFolder;
        imapOutFolder = account.imapOutFolder;
        inRecovery.addAll(indexes);
    }

    public void openImapSession() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        props.setProperty("mail.debug", "false");
        props.setProperty("mail.imap.host", hostname);
        props.setProperty("mail.imap.port", String.valueOf(port));
        props.setProperty("mail.imap.ssl.enable", "true");
        this.session = Session.getDefaultInstance(props, null);
        this.session.setDebug(false);
    }


    public void createBaseFolderIfNeeded() {
        try {
            Files.createDirectories(baseFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readImapEmails() {

        Store store = null;
        try {
            // Connect to message store
            store = session.getStore("imap");
            store.connect( hostname, port, login, password);  //open the inbox folder
            Folder[] folders = store.getDefaultFolder().list();

            // For information only
            for ( var f : folders ) {
                IMAPFolder inbox = (IMAPFolder) store.getFolder(f.getFullName());
                inbox.open(Folder.READ_ONLY);//fetch messages
                Message[] messages = inbox.getMessages();//read messages
                logger.info("😎 >> Number of message read : [{}] [{}]", messages.length,
                        f.getFullName());
            }

            for (String imapFolder : imapOutFolder) {
                readImapSingleFolder(store, imapFolder, Direction.OUT);
            }

            for (String imapFolder : imapInFolder) {
                readImapSingleFolder(store, imapFolder, Direction.IN);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public enum EmailSep {
        BRACKETS,
        PARENTHESIS
    }

    private String[] extractContactInfo(String contact, EmailSep sep) {
        String sep1 = "";
        String sep2 = "";
        switch (sep) {
            case BRACKETS -> {
                sep1 = "<";
                sep2 = ">";
            }
            case PARENTHESIS -> {
                sep1 = "(";
                sep2 = ")";
            }
        }
        var pos = contact.lastIndexOf(sep1);
        var pos2 = contact.lastIndexOf(sep2);
        String casual = "";
        String email = contact;
        if ( pos > 0 ) {
            casual = contact.substring(0, pos).trim();
        }
        if ( pos > 0 && pos2 > 0 ) {
            email = contact.substring(pos+1, pos2).toLowerCase();
        }
        return new String[]{ casual, email } ;
    }

    private final static int MAX_MESSAGE_TO_READ = 10_000; // change it to 10_000

    private void readImapSingleFolder(Store store, String imapFolder, Direction direction) throws MessagingException, IOException {

        logger.info("🚀 Read imap single folder : [{}]", imapFolder);

        IMAPFolder inbox = (IMAPFolder) store.getFolder(imapFolder);
        Message[] messages = new Message[] {};
        try {
            inbox.open(Folder.READ_ONLY);
            messages = inbox.getMessages();
        }  catch (FolderNotFoundException e) {
            logger.debug("💣 Imap Folder does not exist : {}", imapFolder);
        } catch (MessagingException e) {
            logger.debug("💣 Imap Folder open error : {}", imapFolder);
        }

        logger.info("😎 Number of message read : [{}]", messages.length);

        // Read all the messages in the IMAP account
        int nbMessageToRead = Math.min(messages.length, MAX_MESSAGE_TO_READ);
        for (int i = 0; i < nbMessageToRead; i++) {
            Message msg = messages[i];
            Address[] fromAddress = msg.getFrom();
            String from = fromAddress[0].toString();
            String subject =  msg.getSubject() == null ? "" :  msg.getSubject().trim();

            logger.debug("🐞 Message number : [{}], subject : [{}]", i, subject);

            String id = null;
            if (msg instanceof IMAPMessage) {
                id = ((IMAPMessage) msg).getMessageID();
                if (id!=null) id = id.trim();
            }

            // Loop the messages and check if already existing
            if ( id != null && ((! indexes.contains(id)) || (recover && inRecovery.contains(id)))) {

                var refEmail = switch (direction) {
                    case IN -> {
                        String sender = "";
                        if (msg.getFrom() != null && msg.getFrom().length > 0) {
                            sender = msg.getFrom()[0].toString().trim();
                        }
                        subject = "🔴 " + subject;
                        yield sender;
                    }
                    case OUT -> {
                        String rcv = "";
                        if (msg.getRecipients(Message.RecipientType.TO) != null
                                && msg.getRecipients(Message.RecipientType.TO).length > 0) {
                            rcv = msg.getRecipients(Message.RecipientType.TO)[0].toString().trim();
                        }
                        subject = "🔵 " + subject;
                        yield rcv;
                    }
                };

                String[] infos = extractContactInfo(refEmail, EmailSep.BRACKETS);
                String casual = infos[0];
                String email  = infos[1];

                if ( this.contactMap.containsKey(email)) {
                    casual = this.contactMap.get(email);
                } else {
                    this.contactMap.put(email, casual);
                }

                var extraFolder = casual + " (" + email + ")";

                String terminalFolder = switch (group.toString()) {
                    case "true" -> sanitizeFilename(extraFolder);
                    case "false" -> imapFolder;
                    default -> "";
                };
                logger.info("😎 New message saved, subject : [{}]", subject);
                processSaveToFile(msg, subject, terminalFolder);
            }
        }

        logger.info("🏁 Read imap single folder : [{}]", imapFolder);
    }


    private Path buildEmailFolder(String terminalFolder) {
        Path p = Paths.get( baseFolder.toString() , subFolder, terminalFolder );
        return p;
    }


    private void processSaveToFile (javax.mail.Message msg, String subject,
                                    String terminalFolder)
            throws MessagingException, IOException {

        // Build the target folder
        Path targetFolder = buildEmailFolder(terminalFolder);
        Files.createDirectories(targetFolder);

        Path pathName = Paths.get( targetFolder.toString(), sanitizeFilename(subject) + ".eml" );
        int count =  1;
        while ( Files.exists(pathName) ) {
            pathName = Paths.get(targetFolder.toString() , sanitizeFilename(subject) + "_"
                    +  String.valueOf(count)
                    + ".eml" );
            count++;
        }

        String id = null;
        if (msg instanceof IMAPMessage) {
            id = ((IMAPMessage) msg).getMessageID().trim();
        }
        logger.info("🔥 Save message in pathName=[{}]", pathName);
        logger.debug("🐞 Message ID : {}", id);

        // Write the message on disk
        OutputStream out = new FileOutputStream(pathName.toFile());
        try {
            msg.writeTo(out);
        }
        finally {
            out.flush();
            out.close();
        }

        // Add the incoming message into the list of index.
        if (!indexes.contains(id)) {
            indexes.add(id);
        }

        // Change the file date
        Date rcv = msg.getReceivedDate();
        FileTime ft = FileTime.from(rcv.toInstant());
        Files.setLastModifiedTime(pathName, ft);
    }


    private static String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<>\"]", "").trim();
    }

    public void readIndexes( ) {
        readIndexFolder(this.baseFolder.toFile());
    }

    private void readIndexFolder(File folder ) {
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if ( group ) {
                    indexFolder(file);
                }
                readIndexFolder(file);
            } else {
                this.indexEmail(file);
            }
        }
    }


    private void indexFolder(File folder) {
        if (folder.getName().contains("@")) {
            logger.info("😎 Indexing Folder : [{}]", folder.getName());
            var infos = extractContactInfo(folder.getName(), EmailSep.PARENTHESIS);
            if ( infos[1] != null ) {
                this.contactMap.put(infos[1], infos[0]);
            }
        }
    }


    private void indexEmail(File emlFile) {

        logger.info( "😎 Indexing Email : [{}]", emlFile.getName());

        if  ( emlFile.getPath().indexOf(".eml") <= 0 ) {
            return;
        }
        try (
                InputStream source = new FileInputStream(emlFile)
        ) {

            MimeMessage message = new MimeMessage(this.session, source);
            String id = message.getMessageID();
            if (!indexes.contains(id)) {
                indexes.add(id);
            }
            // We found it so it's no longer "inRecovery"
            inRecovery.remove(id);

        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }

    }


    // STATIC ROUTINE
    public static void readIndexFile(String emailFolder) {

        baseFolder = Paths.get(emailFolder);

        Path p = Paths.get(baseFolder.toString(), "message-id.idx");
        if ( ! Files.exists(p) ) {
            return;
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(p.toFile());
            ObjectInputStream ois = new ObjectInputStream(fis);
            indexes = (List<String>) ois.readObject();
            ois.close();
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }

    }

    // STATIC ROUTINE
    public static void writeIndexes() {

        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(
                    new FileOutputStream( Paths.get(baseFolder.toString(), "message-id.idx").toFile())
            );
            oos.writeObject(indexes);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}