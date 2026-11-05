package com.alertamujer.backend.contacts.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de lectura del panel de contactos. Cada consulta de un recurso individual usa la misma
 * condición de relación para impedir que una persona sin autorización distinga un recurso inexistente
 * de uno que no puede leer.
 */
@Repository
public class ContactAlertRepository {

    private static final String AUTHORIZED_EMERGENCY_JOINS = """
            from emergency.emergencies emergency
              join identity.users owner on owner.user_id = emergency.user_id
              join contacts.emergency_contacts relation on (
                   (relation.owner_user_id = ? and relation.contact_user_id = owner.user_id)
                or (relation.contact_user_id = ? and relation.owner_user_id = owner.user_id))
              join identity.users contact on contact.user_id = ?
            """;

    private static final String AUTHORIZED_EMERGENCY_CONDITIONS = """
             where relation.relationship_status = 'ACCEPTED'
               and contact.role = 'USER' and contact.account_status = 'ENABLED'
               and owner.role = 'USER' and owner.account_status = 'ENABLED'
               and emergency.status <> 'FINALIZED'
            """;

    private final JdbcTemplate jdbc;

    public ContactAlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AlertData> findAuthorizedAlerts(UUID contactUserId, int size, long offset) {
        return jdbc.query("""
                select emergency.emergency_id, emergency.status, concat_ws(' ', owner.first_names, owner.last_names),
                       emergency.message_snapshot, emergency.started_at
                """ + AUTHORIZED_EMERGENCY_JOINS + AUTHORIZED_EMERGENCY_CONDITIONS
                + " order by emergency.started_at desc, emergency.emergency_id asc limit ? offset ?",
                alertMapper(), contactUserId, contactUserId, contactUserId, size, offset);
    }

    public long countAuthorizedAlerts(UUID contactUserId) {
        Long total = jdbc.queryForObject("select count(*) " + AUTHORIZED_EMERGENCY_JOINS + AUTHORIZED_EMERGENCY_CONDITIONS, Long.class,
                contactUserId, contactUserId, contactUserId);
        return total == null ? 0L : total;
    }

    public Optional<AlertDetailData> findAuthorizedAlertDetail(UUID contactUserId, UUID emergencyId) {
        List<AlertDetailData> rows = jdbc.query("""
                select emergency.emergency_id, emergency.status, concat_ws(' ', owner.first_names, owner.last_names),
                       emergency.started_at, emergency.message_snapshot, latest.latitude, latest.longitude, latest.captured_at
                """ + AUTHORIZED_EMERGENCY_JOINS + """
                  left join lateral (
                      select latitude, longitude, captured_at
                        from emergency.emergency_locations
                       where emergency_id = emergency.emergency_id
                       order by received_at desc, location_id desc
                       limit 1
                  ) latest on true
                """ + AUTHORIZED_EMERGENCY_CONDITIONS + " and emergency.emergency_id = ?", detailMapper(),
                contactUserId, contactUserId, contactUserId, emergencyId);
        return rows.stream().findFirst();
    }

    public List<EvidenceData> findAuthorizedEvidence(UUID contactUserId, UUID emergencyId) {
        return jdbc.query("""
                select evidence.evidence_id, evidence.file_reference, evidence.mime_type, evidence.file_size_bytes, evidence.received_at
                """ + AUTHORIZED_EMERGENCY_JOINS + """
                  join emergency.emergency_evidences evidence on evidence.emergency_id = emergency.emergency_id
                """ + AUTHORIZED_EMERGENCY_CONDITIONS + " and emergency.emergency_id = ? order by evidence.evidence_sequence",
                evidenceMapper(), contactUserId, contactUserId, contactUserId, emergencyId);
    }

    public Optional<EvidenceData> findAuthorizedEvidence(UUID contactUserId, UUID emergencyId, UUID evidenceId) {
        List<EvidenceData> rows = jdbc.query("""
                select evidence.evidence_id, evidence.file_reference, evidence.mime_type, evidence.file_size_bytes, evidence.received_at
                """ + AUTHORIZED_EMERGENCY_JOINS + """
                  join emergency.emergency_evidences evidence on evidence.emergency_id = emergency.emergency_id
                """ + AUTHORIZED_EMERGENCY_CONDITIONS + " and emergency.emergency_id = ? and evidence.evidence_id = ?",
                evidenceMapper(), contactUserId, contactUserId, contactUserId, emergencyId, evidenceId);
        return rows.stream().findFirst();
    }

    private static org.springframework.jdbc.core.RowMapper<AlertData> alertMapper() {
        return (rs, row) -> new AlertData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                instant(rs.getTimestamp(5)));
    }

    private static org.springframework.jdbc.core.RowMapper<AlertDetailData> detailMapper() {
        return (rs, row) -> new AlertDetailData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4)), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), instant(rs.getTimestamp(8)));
    }

    private static org.springframework.jdbc.core.RowMapper<EvidenceData> evidenceMapper() {
        return (rs, row) -> new EvidenceData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
                instant(rs.getTimestamp(5)));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record AlertData(UUID id, String status, String userDisplayName, String messageSnapshot, Instant startedAt) { }
    public record AlertDetailData(UUID id, String status, String userDisplayName, Instant startedAt, String messageSnapshot,
            BigDecimal latitude, BigDecimal longitude, Instant capturedAt) { }
    public record EvidenceData(UUID id, String reference, String mimeType, int sizeBytes, Instant receivedAt) { }
}
