package com.adags.modules.encounters.application;

import com.adags.modules.encounters.DepartmentFacade;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DepartmentManagementService implements DepartmentFacade {
    private final NamedParameterJdbcTemplate jdbc;
    public DepartmentManagementService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Category(UUID id, String code, String name, boolean active) {}
    public record Unit(UUID id, UUID facilityId, UUID categoryId, String code, String name, String description,
                       String unitType, boolean consultationEnabled, boolean active, UUID hodUserId, String hodUsername) {}
    public record Staff(UUID userId, String username, boolean primary, boolean active) {}
    public record UnitUpdate(String name, String description, String unitType, Boolean consultationEnabled, Boolean active) {}
    public record UnitCreate(UUID facilityId, UUID categoryId, String code, String name, String description,
                             String unitType, boolean consultationEnabled) {}

    public UUID facilityId(UUID unitId) {
        var result=jdbc.query("SELECT facility_id FROM department WHERE id=:id",
            new MapSqlParameterSource("id",unitId),(rs,n)->(UUID)rs.getObject(1));
        if (result.isEmpty()) throw new IllegalArgumentException("Clinic not found.");
        return result.get(0);
    }
    public List<Category> categories() {
        return jdbc.query("SELECT id,code,name,active FROM department_category ORDER BY name", (rs,n) ->
            new Category((UUID)rs.getObject(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)));
    }
    public List<Unit> units(boolean includeInactive) {
        return jdbc.query("""
            SELECT d.id,d.facility_id,d.category_id,d.code,d.name,d.description,d.unit_type,d.consultation_enabled,d.active,
                   h.user_id,u.username FROM department d
            LEFT JOIN department_hod h ON h.department_id=d.id AND h.ended_at IS NULL
            LEFT JOIN app_user u ON u.id=h.user_id
            WHERE (:includeInactive OR d.active) ORDER BY d.name
            """, new MapSqlParameterSource("includeInactive",includeInactive), (rs,n) -> new Unit(
                (UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),rs.getString(4),rs.getString(5),
                rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getBoolean(9),
                (UUID)rs.getObject(10),rs.getString(11)));
    }
    public List<Staff> staff(UUID unitId) {
        return jdbc.query("SELECT s.user_id,u.username,s.is_primary,u.status='ACTIVE' FROM department_staff s JOIN app_user u ON u.id=s.user_id WHERE s.department_id=:id ORDER BY u.username",
            new MapSqlParameterSource("id",unitId),(rs,n)->new Staff((UUID)rs.getObject(1),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4)));
    }
    public List<DepartmentFacade.Assignment> assignments(UUID userId) {
        return jdbc.query("""
            SELECT s.department_id,s.is_primary,h.id IS NOT NULL FROM department_staff s
            LEFT JOIN department_hod h ON h.department_id=s.department_id AND h.user_id=s.user_id AND h.ended_at IS NULL
            WHERE s.user_id=:userId ORDER BY s.is_primary DESC
            """, new MapSqlParameterSource("userId",userId), (rs,n)->new DepartmentFacade.Assignment((UUID)rs.getObject(1),rs.getBoolean(2),rs.getBoolean(3)));
    }
    @Transactional
    public void assign(UUID userId, List<UUID> unitIds, UUID primaryId, UUID actorId) {
        if (unitIds == null) throw new IllegalArgumentException("Choose assigned clinics.");
        if ((!unitIds.isEmpty() && primaryId==null) || unitIds.stream().distinct().count()!=unitIds.size() || (primaryId!=null && !unitIds.contains(primaryId)))
            throw new IllegalArgumentException("Primary clinic must be in the assigned clinics; duplicates are not allowed.");
        var p=new MapSqlParameterSource("userId",userId).addValue("actorId",actorId);
        Integer eligible=jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id=:userId AND status='ACTIVE'",p,Integer.class);
        if (eligible==null || eligible!=1) throw new IllegalArgumentException("An active registered user is required.");
        Integer hodRemoval=jdbc.queryForObject("SELECT count(*) FROM department_hod WHERE user_id=:userId AND ended_at IS NULL AND department_id NOT IN (:ids)",
            p.addValue("ids",unitIds.isEmpty()?List.of(new UUID(0,0)):unitIds),Integer.class);
        if (hodRemoval!=null && hodRemoval>0) throw new IllegalArgumentException("Replace or explicitly vacate the user's HOD appointments before removing assignments.");
        if (!unitIds.isEmpty()) {
            Integer valid=jdbc.queryForObject("SELECT count(*) FROM department d JOIN department_category c ON c.id=d.category_id WHERE d.id IN (:ids) AND d.active AND c.active",p,Integer.class);
            if (valid==null || valid!=unitIds.size()) throw new IllegalArgumentException("All assigned clinics must be active.");
        }
        jdbc.update("DELETE FROM department_staff WHERE user_id=:userId AND department_id NOT IN (:ids)",p);
        jdbc.update("UPDATE department_staff SET is_primary=false WHERE user_id=:userId",p);
        for(UUID id:unitIds) {
            jdbc.update("INSERT INTO department_staff(department_id,user_id,is_primary,assigned_by) VALUES (:id,:userId,false,:actorId) ON CONFLICT (department_id,user_id) DO NOTHING",p.addValue("id",id));
        }
        if(primaryId!=null) jdbc.update("UPDATE department_staff SET is_primary=true WHERE user_id=:userId AND department_id=:id",p.addValue("id",primaryId));
    }
    @Transactional
    public void appoint(UUID unitId, UUID userId, UUID actorId) {
        var p=new MapSqlParameterSource("id",unitId).addValue("userId",userId).addValue("actorId",actorId);
        // Serialize concurrent replacements for the same department.
        var locks=jdbc.query("SELECT id FROM department WHERE id=:id AND active FOR UPDATE",p,(rs,n)->(UUID)rs.getObject(1));
        if(locks.isEmpty()) throw new IllegalArgumentException("Choose an active clinic.");
        Integer eligible=jdbc.queryForObject("""
            SELECT count(*) FROM department_staff s JOIN app_user u ON u.id=s.user_id
            WHERE s.department_id=:id AND s.user_id=:userId AND u.status='ACTIVE'
            """,p,Integer.class);
        if(eligible==null || eligible!=1) throw new IllegalArgumentException("HOD must be an active, assigned staff member.");
        Integer same=jdbc.queryForObject("SELECT count(*) FROM department_hod WHERE department_id=:id AND user_id=:userId AND ended_at IS NULL",p,Integer.class);
        if(same!=null && same==1) return;
        jdbc.update("UPDATE department_hod SET ended_at=now(),ended_by=:actorId WHERE department_id=:id AND ended_at IS NULL",p);
        jdbc.update("INSERT INTO department_hod(department_id,user_id,appointed_by) VALUES (:id,:userId,:actorId)",p);
    }
    @Transactional
    public void vacateForUser(UUID unitId, UUID userId, UUID actorId) {
        jdbc.update("UPDATE department_hod SET ended_at=now(),ended_by=:actorId WHERE department_id=:id AND user_id=:userId AND ended_at IS NULL",
            new MapSqlParameterSource("id",unitId).addValue("userId",userId).addValue("actorId",actorId));
    }
    @Transactional
    public void vacate(UUID unitId, UUID actorId) {
        jdbc.update("UPDATE department_hod SET ended_at=now(),ended_by=:actorId WHERE department_id=:id AND ended_at IS NULL",
            new MapSqlParameterSource("id",unitId).addValue("actorId",actorId));
    }
    @Transactional
    public UUID create(UnitCreate value, UUID actorId) {
        validateType(value.unitType(),value.consultationEnabled());
        if(value.facilityId()==null || value.categoryId()==null || value.code()==null || value.code().isBlank() || value.name()==null || value.name().isBlank())
            throw new IllegalArgumentException("Facility, category, code and name are required.");
        if (!value.code().trim().matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Clinic code may contain letters, numbers and underscores only.");
        Integer category=jdbc.queryForObject("SELECT count(*) FROM department_category WHERE id=:id AND active",new MapSqlParameterSource("id",value.categoryId()),Integer.class);
        if(category==null || category!=1) throw new IllegalArgumentException("Choose an active category.");
        Integer duplicate=jdbc.queryForObject("SELECT count(*) FROM department WHERE facility_id=:facilityId AND (lower(name)=lower(:name) OR code=:code)",
            new MapSqlParameterSource("facilityId",value.facilityId()).addValue("name",value.name().trim()).addValue("code",value.code().trim().toUpperCase()),Integer.class);
        if (duplicate!=null && duplicate>0) throw new IllegalArgumentException("A clinic with this name or code already exists.");
        UUID id=jdbc.queryForObject("""
            INSERT INTO department(facility_id,category_id,code,name,description,unit_type,consultation_enabled)
            VALUES (:facilityId,:categoryId,:code,:name,:description,:unitType,:consultationEnabled) RETURNING id
            """,new MapSqlParameterSource().addValue("facilityId",value.facilityId()).addValue("categoryId",value.categoryId())
            .addValue("code",value.code().trim().toUpperCase()).addValue("name",value.name().trim())
            .addValue("description",value.description()).addValue("unitType",value.unitType())
            .addValue("consultationEnabled",value.consultationEnabled()),UUID.class);
        jdbc.update("INSERT INTO department_change_audit(department_id,actor_user_id,action,new_value) SELECT id,:actorId,'CREATE',to_jsonb(d) FROM department d WHERE id=:id",
            new MapSqlParameterSource("id",id).addValue("actorId",actorId));
        return id;
    }
    private void validateType(String type, boolean consultationEnabled) {
        if (type==null || !java.util.Set.of("CONSULTATION","WARD","DIAGNOSTIC","PHARMACY","THEATRE","SUPPORT").contains(type))
            throw new IllegalArgumentException("Choose a valid clinic unit type.");
        if (!"CONSULTATION".equals(type) && consultationEnabled)
            throw new IllegalArgumentException("Only consultation clinics can receive doctor consultations.");
    }
    @Transactional
    public void update(UUID id, UnitUpdate value, UUID actorId) {
        if(value.name()==null || value.name().isBlank() || value.unitType()==null || value.active()==null || value.consultationEnabled()==null)
            throw new IllegalArgumentException("Name, type and status are required.");
        validateType(value.unitType(),value.consultationEnabled());
        Integer duplicate=jdbc.queryForObject("""
            SELECT count(*) FROM department other JOIN department current ON current.id=:id
            WHERE other.facility_id=current.facility_id AND other.id<>current.id AND lower(other.name)=lower(:name)
            """,new MapSqlParameterSource("id",id).addValue("name",value.name().trim()),Integer.class);
        if (duplicate!=null && duplicate>0) throw new IllegalArgumentException("A clinic with this name already exists.");
        if (!value.active()) {
            Integer count=jdbc.queryForObject("SELECT count(*) FROM department_hod WHERE department_id=:id AND ended_at IS NULL",
                new MapSqlParameterSource("id",id),Integer.class);
            if (count!=null && count>0) throw new IllegalArgumentException("Record a HOD vacancy before deactivating this clinic.");
        }
        var p=new MapSqlParameterSource("id",id).addValue("name",value.name().trim()).addValue("description",value.description())
            .addValue("unitType",value.unitType()).addValue("active",value.active()).addValue("consultationEnabled",value.consultationEnabled());
        var old=jdbc.query("SELECT to_jsonb(d)::text FROM department d WHERE id=:id",p,(rs,n)->rs.getString(1));
        if(old.isEmpty()) throw new IllegalArgumentException("Clinic not found.");
        jdbc.update("UPDATE department SET name=:name,description=:description,unit_type=:unitType,active=:active,consultation_enabled=:consultationEnabled WHERE id=:id",p);
        jdbc.update("""
            INSERT INTO department_change_audit(department_id,actor_user_id,action,previous_value,new_value)
            SELECT :id,:actorId,'UPDATE',CAST(:previous AS jsonb),to_jsonb(d) FROM department d WHERE d.id=:id
            """,p.addValue("actorId",actorId).addValue("previous",old.get(0)));
    }
}
