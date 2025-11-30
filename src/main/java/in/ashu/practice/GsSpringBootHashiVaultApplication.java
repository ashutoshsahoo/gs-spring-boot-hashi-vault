package in.ashu.practice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;

import java.util.Map;

@Slf4j
@SpringBootApplication
public class GsSpringBootHashiVaultApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(GsSpringBootHashiVaultApplication.class, args);
    }

    @Autowired
    private VaultTemplate vaultTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 1. Secret backend
        // You usually would not print a secret to stdout
        log.info("Ensure the secret is created at path using command: vault kv put secret/team-a/github github.oauth2.key=foobar");
        VaultResponse response = vaultTemplate
                .opsForKeyValue("secret", KeyValueBackend.KV_2).get("team-a/github");
        log.info("Value of github.oauth2.key");
        if (response != null) {
            Map<String, Object> responseData = response.getData();
            if (responseData != null) {
                String s = responseData.get("github.oauth2.key").toString();
                log.info(s);
            } else {
                log.info("No data found in path secret/team-a/github");
            }
        } else {
            log.info("Secret path secret/team-a/github does not exist");
        }

        // 2. Transit backend
        // Let's encrypt some data using the Transit backend.
        VaultTransitOperations transitOperations = vaultTemplate.opsForTransit();

        // We need to set up transit first (assuming you didn't set up it yet).
        VaultSysOperations sysOperations = vaultTemplate.opsForSys();

        if (!sysOperations.getMounts().containsKey("transit/")) {
            sysOperations.mount("transit", VaultMount.create("transit"));
            transitOperations.createKey("foo-key");
        }

        // Encrypt a plain-text value
        String ciphertext = transitOperations.encrypt("foo-key", "Secure message");
        log.info("Encrypted value");
        log.info(ciphertext);

        // Decrypt
        String plaintext = transitOperations.decrypt("foo-key", ciphertext);
        log.info("Decrypted value");
        log.info(plaintext);
    }
}
