package lu.isd.imap_reader;

import lu.isd.imap_reader.config.Account;
import lu.isd.imap_reader.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ImapReader {

    private static final Logger logger = LoggerFactory.getLogger(ImapReader.class);

    public static void main(String[] argc) {

        Config conf = (new ConfigService()).getConfig();

        // Read the stored messageId as index
        logger.info("🚀 Read stored indexes");
        ReadMail.readIndexFile(conf.emailFolder);
        logger.info("🏁 Read stored indexes");

        for (Account account : conf.accounts) {

            var rm = new ReadMail(conf, account);

            logger.info("🚀 Open IMAP Session");

            rm.openImapSession();

            logger.info("🏁 End Open IMAP Session");

            logger.info("🚀 Create base folder");
            rm.createBaseFolderIfNeeded();
            logger.info("🏁 Create base folder");

            // Read the messageIDs and contact information mapping of stored messages
            logger.info("🚀 Read indexes");
            rm.readIndexes();
            logger.info("🏁 Read indexes");

            // Read and save the emails
            logger.info("🚀 Process Emails, account name=[{}]", account.name);
            rm.readImapEmails();
            logger.info("🏁 Process Emails, account name=[{}]", account.name);

        }

        logger.info("🚀 Write indexes");
        ReadMail.writeIndexes();
        logger.info("🏁 End Write indexes");
    }

}

