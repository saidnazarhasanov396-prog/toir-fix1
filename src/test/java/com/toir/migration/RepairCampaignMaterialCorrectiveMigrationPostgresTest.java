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

class RepairCampaignMaterialCorrectiveMigrationPostgresTest {
    @Test void postgres17RequiresNamedOverflowRemediationThenAppliesExactQuantityColumns() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String db="task5c_"+UUID.randomUUID().toString().replace("-","");
        String admin=env("TOIR_LOCAL_PG_URL","jdbc:postgresql://localhost:5432/postgres"),url=dbUrl(admin,db);
        try(Connection c=conn(admin);Statement s=c.createStatement()){
            try(ResultSet r=s.executeQuery("SHOW server_version_num")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isBetween(170000,179999);}
            s.execute("CREATE DATABASE "+db);
        }
        try{
            migrate(url,"20260712.5");
            UUID movement=UUID.randomUUID();
            try(Connection c=conn(url);Statement s=c.createStatement()){
                s.execute("INSERT INTO warehouse_stock_ledger_metadata(id,warehouse_id,spare_part_id,legacy_type,submitted_quantity,is_deleted,created_at,updated_at,submitted_occurred_at) VALUES ('"+movement+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','ISSUE',1000000000000000,false,now(),now(),now())");
            }
            assertThatThrownBy(()->migrate(url,"20260712.5.1")).hasStackTraceContaining("RC_V5_1_NUMERIC_OVERFLOW_REMEDIATION_REQUIRED");
            try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("DELETE FROM warehouse_stock_ledger_metadata WHERE id='"+movement+"'");}
            migrate(url,"20260712.5.1");
            for(String table:java.util.List.of("stock_movements","repair_material_usages","repair_material_returns","maintenance_template_spare_part_requirements","maintenance_regulation_spare_part_requirements")){
                try(Connection c=conn(url);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_name='"+table+"' AND column_name='quantity'")){
                    assertThat(r.next()).as(table).isTrue();assertThat(r.getInt(1)).as(table).isEqualTo(19);assertThat(r.getInt(2)).as(table).isEqualTo(4);
                }
            }
            reservationTransitionLockProbe(url);
        }finally{try(Connection c=conn(admin);Statement s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+db+" WITH (FORCE)");}}
    }
    private void reservationTransitionLockProbe(String url)throws Exception{
        UUID id=UUID.randomUUID();
        try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("INSERT INTO reservations(id,quantity,status,is_deleted,created_at,updated_at,stock_status) VALUES ('"+id+"',1.0000,'ACTIVE',false,now(),now(),'AVAILABLE')");}
        Connection first=conn(url);first.setAutoCommit(false);
        try(Statement s=first.createStatement()){s.executeQuery("SELECT status FROM reservations WHERE id='"+id+"' FOR UPDATE").close();}
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try{
            Future<String> contender=executor.submit(()->{try(Connection c=conn(url)){c.setAutoCommit(false);try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT status FROM reservations WHERE id='"+id+"' FOR UPDATE")){r.next();String status=r.getString(1);c.commit();return status;}}});
            Thread.sleep(200);assertThat(contender.isDone()).isFalse();
            try(Statement s=first.createStatement()){s.execute("UPDATE reservations SET status='CANCELLED' WHERE id='"+id+"'");}first.commit();
            assertThat(contender.get(5,TimeUnit.SECONDS)).isEqualTo("CANCELLED");
        }finally{first.close();executor.shutdownNow();}
    }
    private void migrate(String url,String target){assertThat(Flyway.configure().dataSource(url,user(),pass()).locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0").target(target).load().migrate().success).isTrue();}
    private static Connection conn(String u)throws Exception{return DriverManager.getConnection(u,user(),pass());}
    private static String dbUrl(String u,String d){int q=u.indexOf('?');String x=q<0?"":u.substring(q),b=q<0?u:u.substring(0,q);return b.substring(0,b.lastIndexOf('/')+1)+d+x;}
    private static String user(){return env("TOIR_LOCAL_PG_USER","postgres");}private static String pass(){return env("TOIR_LOCAL_PG_PASSWORD","");}private static String env(String n,String d){String v=System.getenv(n);return v==null?d:v;}
}
