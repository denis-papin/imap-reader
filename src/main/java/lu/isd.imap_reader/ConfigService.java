package lu.isd.imap_reader;

import lu.isd.imap_reader.config.Config;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;

public class ConfigService {


    private String definitionPath = "./config.yml";

    private Config config;

    public ConfigService() {
        init();
    }


    public void init() {
        System.out.println("We are loading the configuration");

        Yaml yaml = new Yaml(new Constructor(Config.class));

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(Paths.get(definitionPath).toFile());
            config = yaml.load(inputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Config getConfig() {
        return config;
    }
}
