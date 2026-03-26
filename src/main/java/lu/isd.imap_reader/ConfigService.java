package lu.isd.imap_reader;

import lu.isd.imap_reader.config.Config;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;

public class ConfigService {

    private static final String DEFAULT_DEFINITION_PATH = "./config.yml";

    private final String definitionPath;

    private Config config;

    public ConfigService() {
        this(DEFAULT_DEFINITION_PATH);
    }

    public ConfigService(String definitionPath) {
        this.definitionPath = definitionPath == null || definitionPath.isBlank()
                ? DEFAULT_DEFINITION_PATH
                : definitionPath;
        init();
    }


    public void init() {
        System.out.println("We are loading the configuration from " + definitionPath);

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
