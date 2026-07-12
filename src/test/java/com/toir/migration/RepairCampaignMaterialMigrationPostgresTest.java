package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairCampaignMaterialMigrationPostgresTest {
    @Test void postgres17RejectsInvalidLegacyThenEnforcesExactConcurrentReservationIdentity() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String db="task5_"+UUID.randomUUID().toString().replace("-","");String admin=env("TOIR_LOCAL_PG_URL","jdbc:postgresql://localhost:5432/postgres"),url=dbUrl(admin,db);
        try(Connection c=conn(admin);Statement s=c.createStatement()){try(ResultSet r=s.executeQuery("SHOW server_version_num")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isBetween(170000,179999);}s.execute("CREATE DATABASE "+db);}
        try {
            migrate(url,"20260712.4");
            UUID invalid=UUID.randomUUID();
            try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("INSERT INTO reservations(id,quantity,status,is_deleted,created_at,updated_at,stock_status) VALUES ('"+invalid+"','NaN','ACTIVE',false,now(),now(),'AVAILABLE')");}
            assertThatThrownBy(()->migrate(url,"20260712.5")).hasStackTraceContaining("RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED");
            try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("DELETE FROM reservations WHERE id='"+invalid+"'");}
            migrate(url,"20260712.5");
            try(Connection c=conn(url);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_name='reservations' AND column_name='quantity'")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(19);assertThat(r.getInt(2)).isEqualTo(4);}
            concurrentUniqueProbe(url);
        } finally {try(Connection c=conn(admin);Statement s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+db+" WITH (FORCE)");}}
    }

    private void concurrentUniqueProbe(String url) throws Exception {UUID work=UUID.randomUUID(),requirement=UUID.randomUUID(),spare=UUID.randomUUID();Connection first=conn(url);first.setAutoCommit(false);try(Statement s=first.createStatement()){s.execute("SET session_replication_role=replica");insert(s,UUID.randomUUID(),work,requirement,spare);}ExecutorService executor=Executors.newSingleThreadExecutor();try{Future<String> second=executor.submit(()->{try(Connection c=conn(url)){c.setAutoCommit(false);try(Statement s=c.createStatement()){s.execute("SET session_replication_role=replica");insert(s,UUID.randomUUID(),work,requirement,spare);c.commit();return "inserted";}}catch(Exception e){return e.getMessage();}});Thread.sleep(200);assertThat(second.isDone()).isFalse();first.commit();assertThat(second.get(5,TimeUnit.SECONDS)).contains("uq_reservations_active_work_requirement_spare");}finally{first.close();executor.shutdownNow();}}
    private void insert(Statement s,UUID id,UUID work,UUID requirement,UUID spare)throws Exception{s.execute("INSERT INTO reservations(id,quantity,status,is_deleted,created_at,updated_at,stock_status,work_order_id,requirement_id,spare_part_id) VALUES ('"+id+"',1.2345,'ACTIVE',false,now(),now(),'AVAILABLE','"+work+"','"+requirement+"','"+spare+"')");}
    private void migrate(String url,String target){assertThat(Flyway.configure().dataSource(url,user(),pass()).locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0").target(target).load().migrate().success).isTrue();}
    static Connection conn(String u)throws Exception{return DriverManager.getConnection(u,user(),pass());}static String dbUrl(String u,String d){int q=u.indexOf('?');String x=q<0?"":u.substring(q),b=q<0?u:u.substring(0,q);return b.substring(0,b.lastIndexOf('/')+1)+d+x;}static String user(){return env("TOIR_LOCAL_PG_USER","postgres");}static String pass(){return env("TOIR_LOCAL_PG_PASSWORD","");}static String env(String n,String d){String v=System.getenv(n);return v==null?d:v;}
}
