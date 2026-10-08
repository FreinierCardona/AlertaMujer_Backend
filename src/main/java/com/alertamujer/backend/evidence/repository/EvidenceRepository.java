package com.alertamujer.backend.evidence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access limited to the migrated emergency evidence metadata table. */
@Repository
public class EvidenceRepository {
    private final JdbcTemplate jdbc;

    public EvidenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<EmergencyData> lockOwnedEnabledEmergency(UUID emergencyId, UUID userId) {
        List<EmergencyData> rows = jdbc.query("""
                select emergency_id, status
                  from emergency.emergencies emergency
                  join identity.users owner on owner.user_id = emergency.user_id
                 where emergency.emergency_id = ? and emergency.user_id = ?
                   and owner.role = 'USER' and owner.account_status = 'ENABLED'
                 for update of emergency
                """, (rs, row) -> new EmergencyData(rs.getObject(1, UUID.class), rs.getString(2)), emergencyId, userId);
        return rows.stream().findFirst();
    }

    public Optional<EvidenceData> findAuthorizedEvidence(UUID evidenceId, UUID actorUserId, String role) {
        String ownership = "USER".equals(role) ? "and emergency.user_id = ?" : "";
        List<EvidenceData> rows = jdbc.query("""
                select evidence.evidence_id, evidence.emergency_id, evidence.file_reference, evidence.mime_type,
                       evidence.file_size_bytes, evidence.received_at
                  from emergency.emergency_evidences evidence
                  join emergency.emergencies emergency on emergency.emergency_id = evidence.emergency_id
                 where evidence.evidence_id = ?
                """ + ownership, evidenceMapper(), "USER".equals(role) ? new Object[] {evidenceId, actorUserId}
                : new Object[] {evidenceId});
        return rows.stream().findFirst();
    }

    public List<EvidenceData> findAuthorizedEmergencyEvidence(UUID emergencyId, UUID actorUserId, String role) {
        String ownership = "USER".equals(role) ? "and emergency.user_id = ?" : "";
        return jdbc.query("""
                select evidence.evidence_id, evidence.emergency_id, evidence.file_reference, evidence.mime_type,
                       evidence.file_size_bytes, evidence.received_at
                  from emergency.emergency_evidences evidence
                  join emergency.emergencies emergency on emergency.emergency_id = evidence.emergency_id
                 where emergency.emergency_id = ?
                """ + ownership + " order by evidence.evidence_sequence", evidenceMapper(),
                "USER".equals(role) ? new Object[] {emergencyId, actorUserId} : new Object[] {emergencyId});
    }

    public boolean hasAuthorizedEmergency(UUID emergencyId, UUID actorUserId, String role) {
        String ownership = "USER".equals(role) ? "and user_id = ?" : "";
        Boolean found = jdbc.queryForObject("select exists (select 1 from emergency.emergencies where emergency_id = ? "
                + ownership + ")", Boolean.class, "USER".equals(role) ? new Object[] {emergencyId, actorUserId}
                : new Object[] {emergencyId});
        return Boolean.TRUE.equals(found);
    }

    public int nextSequence(UUID emergencyId) {
        Integer sequence = jdbc.queryForObject("""
                select coalesce(max(evidence_sequence), 0) + 1
                  from emergency.emergency_evidences
                 where emergency_id = ?
                """, Integer.class, emergencyId);
        return sequence == null ? 1 : sequence;
    }

    public void insert(UUID evidenceId, UUID emergencyId, int sequence, String reference, String originalName,
            String originalMimeType, int sizeBytes, Instant receivedAt) {
        jdbc.update("""
                insert into emergency.emergency_evidences (evidence_id, emergency_id, evidence_sequence, file_reference,
                    original_file_name, mime_type, original_mime_type, file_size_bytes, received_at)
                values (?, ?, ?, ?, ?, 'image/webp', ?, ?, ?)
                """, evidenceId, emergencyId, sequence, reference, originalName, originalMimeType, sizeBytes,
                Timestamp.from(receivedAt));
    }

    public List<String> findAllReferences() {
        return jdbc.query("select file_reference from emergency.emergency_evidences", (rs, row) -> rs.getString(1));
    }

    private static org.springframework.jdbc.core.RowMapper<EvidenceData> evidenceMapper() {
        return (rs, row) -> new EvidenceData(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getTimestamp(6).toInstant());
    }

    public record EmergencyData(UUID id, String status) { }
    public record EvidenceData(UUID id, UUID emergencyId, String reference, String mimeType, int sizeBytes, Instant receivedAt) { }
}
