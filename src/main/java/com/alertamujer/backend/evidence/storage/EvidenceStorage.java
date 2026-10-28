package com.alertamujer.backend.evidence.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import org.springframework.web.multipart.MultipartFile;

/** Interfaz de almacenamiento exclusiva de este módulo; no es un servicio genérico de archivos. */
public interface EvidenceStorage {
    StoredEvidenceFile store(MultipartFile source) throws IOException;
    InputStream open(String reference) throws IOException;
    void deleteAfterPersistenceFailure(String reference);
    int deleteUnreferencedOlderThan(Collection<String> references, Instant cutoff);
}
