package com.adags.db;

import com.adags.modules.encounters.application.DepartmentManagementService;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ClinicalDepartmentManagementTest extends IntegrationTestSupport {
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DepartmentManagementService departments;

    @Test void seedsAllCategoriesAndUnitsWithoutDuplicateCodes() {
        assertThat(departments.categories()).hasSize(4);
        assertThat(departments.units(false).stream().map(DepartmentManagementService.Unit::code).toList())
            .contains("OPD","EMERGENCY","INTERNAL_MEDICINE","PAEDIATRICS","GENERAL_SURGERY","ORTHOPAEDICS","DENTAL","IPD",
                "CARDIOLOGY","DIABETES","HYPERTENSION","DERMATOLOGY","OPHTHALMOLOGY","ENT","NEUROLOGY","PSYCHIATRY",
                "UROLOGY","GASTROENTEROLOGY","NEPHROLOGY","ONCOLOGY","PHYSIOTHERAPY","NUTRITION","HIV_CTC","TB",
                "RCH","ANC","PNC","FAMILY_PLANNING","OBGYN","MATERNITY","LABORATORY","RADIOLOGY","ULTRASOUND",
                "PHARMACY","OPERATING_THEATRE","NURSING","ICU","DIALYSIS","MINOR_PROCEDURES");
        Integer duplicateCount=jdbc.queryForObject("""
            SELECT count(*) FROM (SELECT facility_id,code FROM department GROUP BY facility_id,code HAVING count(*)>1) x
            """,new MapSqlParameterSource(),Integer.class);
        assertThat(duplicateCount).isZero();
        var unit=departments.units(false).stream().filter(u -> "ULTRASOUND".equals(u.code())).findFirst().orElseThrow();
        assertThat(unit.consultationEnabled()).isFalse();
        assertThat(unit.unitType()).isEqualTo("DIAGNOSTIC");
        int before=departments.units(false).size();
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO department(facility_id,category_id,code,name)
            SELECT facility_id,category_id,code,name FROM department WHERE id=:id
            ON CONFLICT (facility_id,code) DO NOTHING
            """,new MapSqlParameterSource("id",unit.id()))).isInstanceOf(DataAccessException.class);
        assertThat(departments.units(false)).hasSize(before);
    }

    @Test void assignmentsAndHodReplacementsPreserveHistory() {
        UUID first=user(), second=user(), actor=user();
        UUID unit=departments.units(false).stream().filter(u -> "CARDIOLOGY".equals(u.code())).findFirst().orElseThrow().id();
        departments.assign(first,List.of(unit),unit,actor);
        departments.assign(second,List.of(unit),unit,actor);
        assertThat(departments.assignments(first)).singleElement().satisfies(a -> assertThat(a.primary()).isTrue());
        departments.appoint(unit,first,actor);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO department_hod(department_id,user_id,appointed_by) VALUES (:id,:userId,:actorId)",
            new MapSqlParameterSource("id",unit).addValue("userId",second).addValue("actorId",actor)))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=:id",
            new MapSqlParameterSource("id",first))).isInstanceOf(DataAccessException.class);
        assertThat(departments.units(false).stream().filter(u -> u.id().equals(unit)).findFirst().orElseThrow().hodUserId()).isEqualTo(first);
        assertThatThrownBy(() -> departments.assign(first,List.of(),null,actor)).isInstanceOf(IllegalArgumentException.class);
        departments.appoint(unit,second,actor);
        var history=jdbc.query("SELECT user_id,ended_at,ended_by FROM department_hod WHERE department_id=:id ORDER BY appointed_at,id",
            new MapSqlParameterSource("id",unit),(rs,n)->new Object[]{rs.getObject(1),rs.getTimestamp(2),rs.getObject(3)});
        assertThat(history).hasSize(2);
        assertThat(history.stream().filter(row -> row[1]==null)).hasSize(1);
        assertThat(history.stream().filter(row -> row[1]!=null && actor.equals(row[2]))).hasSize(1);
        departments.vacate(unit,actor);
    }

    @Test void rejectsInactiveOrUnassignedHod() {
        UUID user=user(), actor=user();
        UUID unit=departments.units(false).stream().filter(u -> "DIABETES".equals(u.code())).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> departments.appoint(unit,user,actor)).isInstanceOf(IllegalArgumentException.class);
        departments.assign(user,List.of(unit),unit,actor);
        jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=:id",new MapSqlParameterSource("id",user));
        assertThatThrownBy(() -> departments.appoint(unit,user,actor)).isInstanceOf(IllegalArgumentException.class);
    }

    private UUID user() {
        return jdbc.queryForObject("INSERT INTO app_user(username,password_hash) VALUES (:name,'test') RETURNING id",
            new MapSqlParameterSource("name","department-test-"+UUID.randomUUID()),UUID.class);
    }
}
