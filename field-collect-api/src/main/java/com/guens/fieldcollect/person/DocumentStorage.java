package com.guens.fieldcollect.person;

import com.guens.fieldcollect.common.crypto.CryptoService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/** Stocke images de pièces et signatures chiffrées (AES-GCM) sous un nom aléatoire, dans MinIO. */
@Component
public class DocumentStorage {

    private final ObjectStorage objects;
    private final CryptoService crypto;

    public DocumentStorage(ObjectStorage objects, CryptoService crypto) {
        this.objects = objects;
        this.crypto = crypto;
    }

    /** @return clé d'objet à enregistrer en base */
    public String store(byte[] image, String extension) {
        return store("documents", image, extension);
    }

    public String storeSignature(byte[] png) {
        return store("signatures", png, "png");
    }

    private String store(String folder, byte[] data, String extension) {
        LocalDate d = LocalDate.now();
        String key = "%s/%d/%02d/%s.%s.enc".formatted(folder, d.getYear(), d.getMonthValue(), UUID.randomUUID(), extension);
        objects.put(key, crypto.encrypt(data), "application/octet-stream");
        return key;
    }

    public byte[] load(String key) {
        return crypto.decrypt(objects.get(key));
    }
}
