package lu.isd.imap_reader.config;

import java.util.List;

public class Account {
    public String name;
    public Boolean group;
    public Boolean recover;
    public String login;
    public String password;
    public String server;
    public Integer port;
    public List<String> imapOutFolder;
    public List<String> imapInFolder;

    public Account() {
    }
}
