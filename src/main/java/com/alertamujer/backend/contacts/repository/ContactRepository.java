package com.alertamujer.backend.contacts.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL for the existing contact table and its canonical-pair constraint. */
@Repository
public class ContactRepository {

    private final JdbcTemplate jdbc;

    public ContactRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserData> findEnabledUser(UUID userId) {
        return user("where user_id = ? and role = 'USER' and account_status = 'ENABLED'", userId);
    }

    public Optional<UserData> findEnabledUserByUsername(String username) {
        return user("where username = ? and role = 'USER' and account_status = 'ENABLED'", username);
    }

    public List<DirectoryUserData> findDirectory(UUID actorId, String query, int size, long offset) {
        String filter = query == null ? "" : query.trim();
        return jdbc.query("""
                select username::text, first_names, last_names
                  from identity.users
                 where user_id <> ? and role = 'USER' and account_status = 'ENABLED'
                   and (? = '' or username::text ilike '%' || ? || '%'
                        or first_names ilike '%' || ? || '%' or last_names ilike '%' || ? || '%')
                 order by username asc
                 limit ? offset ?
                """, (rs, row) -> new DirectoryUserData(rs.getString(1), rs.getString(2), rs.getString(3)),
                actorId, filter, filter, filter, filter, size, offset);
    }

    public long countDirectory(UUID actorId, String query) {
        String filter = query == null ? "" : query.trim();
        Long total = jdbc.queryForObject("""
                select count(*) from identity.users
                 where user_id <> ? and role = 'USER' and account_status = 'ENABLED'
                   and (? = '' or username::text ilike '%' || ? || '%'
                        or first_names ilike '%' || ? || '%' or last_names ilike '%' || ? || '%')
                """, Long.class, actorId, filter, filter, filter, filter);
        return total == null ? 0L : total;
    }

    public boolean insertPending(UUID contactId, UUID ownerId, UUID targetId, Instant expiresAt, Instant now) {
        return jdbc.update("""
                insert into contacts.emergency_contacts (contact_id, owner_user_id, contact_user_id, relationship_status,
                    expires_at, created_at, updated_at)
                values (?, ?, ?, 'PENDING', ?, ?, ?)
                on conflict (least(owner_user_id, contact_user_id), greatest(owner_user_id, contact_user_id)) do nothing
                """, contactId, ownerId, targetId, Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public Optional<ContactData> lockCanonicalPair(UUID firstUserId, UUID secondUserId) {
        List<ContactData> rows = jdbc.query("""
                select contact_id, owner_user_id, contact_user_id, relationship_status, expires_at, status_changed_at
                  from contacts.emergency_contacts
                 where least(owner_user_id, contact_user_id) = least(?, ?)
                   and greatest(owner_user_id, contact_user_id) = greatest(?, ?)
                 for update
                """, contactMapper(), firstUserId, secondUserId, firstUserId, secondUserId);
        return rows.stream().findFirst();
    }

    public Optional<ContactData> lockContact(UUID contactId) {
        List<ContactData> rows = jdbc.query("""
                select contact_id, owner_user_id, contact_user_id, relationship_status, expires_at, status_changed_at
                  from contacts.emergency_contacts where contact_id = ? for update
                """, contactMapper(), contactId);
        return rows.stream().findFirst();
    }

    public void markExpired(UUID contactId, Instant now) {
        jdbc.update("""
                update contacts.emergency_contacts
                   set relationship_status = 'EXPIRED', expires_at = null, status_changed_at = ?, updated_at = ?
                 where contact_id = ? and relationship_status = 'PENDING' and expires_at <= ?
                """, Timestamp.from(now), Timestamp.from(now), contactId, Timestamp.from(now));
    }

    public void accept(UUID contactId, Instant now) {
        jdbc.update("""
                update contacts.emergency_contacts
                   set relationship_status = 'ACCEPTED', expires_at = null, status_changed_at = ?, updated_at = ?
                 where contact_id = ?
                """, Timestamp.from(now), Timestamp.from(now), contactId);
    }

    public void reject(UUID contactId, Instant now) {
        jdbc.update("""
                update contacts.emergency_contacts
                   set relationship_status = 'REJECTED', expires_at = null, status_changed_at = ?, updated_at = ?
                 where contact_id = ?
                """, Timestamp.from(now), Timestamp.from(now), contactId);
    }

    public void reinvite(UUID contactId, Instant expiresAt, Instant now) {
        jdbc.update("""
                update contacts.emergency_contacts
                   set relationship_status = 'PENDING', expires_at = ?, status_changed_at = null, updated_at = ?
                 where contact_id = ? and relationship_status = 'EXPIRED'
                """, Timestamp.from(expiresAt), Timestamp.from(now), contactId);
    }

    public void expirePendingForUser(UUID userId, Instant now) {
        jdbc.update("""
                update contacts.emergency_contacts
                   set relationship_status = 'EXPIRED', expires_at = null, status_changed_at = ?, updated_at = ?
                 where (owner_user_id = ? or contact_user_id = ?) and relationship_status = 'PENDING' and expires_at <= ?
                """, Timestamp.from(now), Timestamp.from(now), userId, userId, Timestamp.from(now));
    }

    public List<OwnContactData> findOwnContacts(UUID userId, int size, long offset) {
        return jdbc.query("""
                select relation.contact_id, relation.relationship_status, relation.expires_at,
                       other.role = 'USER' and other.account_status = 'ENABLED' as eligible
                  from contacts.emergency_contacts relation
                  join identity.users other on other.user_id =
                       case when relation.owner_user_id = ? then relation.contact_user_id else relation.owner_user_id end
                 where relation.owner_user_id = ? or relation.contact_user_id = ?
                 order by relation.updated_at desc, relation.contact_id asc
                 limit ? offset ?
                """, (rs, row) -> new OwnContactData(rs.getObject(1, UUID.class), rs.getString(2), instant(rs.getTimestamp(3)),
                rs.getBoolean(4)), userId, userId, userId, size, offset);
    }

    public long countOwnContacts(UUID userId) {
        Long total = jdbc.queryForObject("""
                select count(*) from contacts.emergency_contacts
                 where owner_user_id = ? or contact_user_id = ?
                """, Long.class, userId, userId);
        return total == null ? 0L : total;
    }

    private Optional<UserData> user(String where, Object value) {
        List<UserData> rows = jdbc.query("select user_id, username::text from identity.users " + where,
                (rs, row) -> new UserData(rs.getObject(1, UUID.class), rs.getString(2)), value);
        return rows.stream().findFirst();
    }

    private static org.springframework.jdbc.core.RowMapper<ContactData> contactMapper() {
        return (rs, row) -> new ContactData(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record UserData(UUID id, String username) { }
    public record DirectoryUserData(String username, String firstNames, String lastNames) { }
    public record ContactData(UUID id, UUID ownerId, UUID targetId, String status, Instant expiresAt, Instant statusChangedAt) { }
    public record OwnContactData(UUID id, String status, Instant expiresAt, boolean eligible) { }
}
