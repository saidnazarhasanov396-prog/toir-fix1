package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairCampaignMaterialCorrectiveMigrationPostgresTest {
    @Test void postgres17RejectsEveryNegativeLegacySourceAndPreservesV5ForRemediation() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String db="task5n_"+UUID.randomUUID().toString().replace("-","");
        String admin=env("TOIR_LOCAL_PG_URL","jdbc:postgresql://localhost:5432/postgres"),url=dbUrl(admin,db);
        try(Connection c=conn(admin);Statement s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
        try{
            migrate(url,"20260712.5");
            for(LegacyNegative source:LegacyNegative.values()){
                source.insert(url);
                assertThatThrownBy(()->migrate(url,"20260712.5.1"))
                        .as(source.name()).hasStackTraceContaining("RC_V5_1_NUMERIC_OVERFLOW_REMEDIATION_REQUIRED");
                assertThat(Flyway.configure().dataSource(url,user(),pass()).locations("classpath:db/migration").load()
                        .info().current().getVersion().getVersion()).as(source.name()).isEqualTo("20260712.5");
                source.cleanup(url);
            }
            migrate(url,"20260712.5.1");
        }finally{try(Connection c=conn(admin);Statement s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+db+" WITH (FORCE)");}}
    }
    @Test void postgres17RequiresNamedOverflowRemediationThenAppliesExactQuantityColumns() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String db="task5c_"+UUID.randomUUID().toString().replace("-","");
        String admin=env("TOIR_LOCAL_PG_URL","jdbc:postgresql://localhost:5432/postgres"),url=dbUrl(admin,db);
        try(Connection c=conn(admin);Statement s=c.createStatement()){
            try(ResultSet r=s.executeQuery("SHOW server_version_num")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isBetween(170000,179999);}
            s.execute("CREATE DATABASE "+db);
        }
        try{
            migrate(url,"20260712.4");
            UUID invalidV5=UUID.randomUUID();
            try(Connection c=conn(url);Statement s=c.createStatement()){
                s.execute("INSERT INTO reservations(id,quantity,status,is_deleted,created_at,updated_at,stock_status) VALUES ('"+invalidV5+"',-1.0,'ACTIVE',false,now(),now(),'AVAILABLE')");
            }
            assertThatThrownBy(()->migrate(url,"20260712.5"))
                    .hasStackTraceContaining("RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED");
            try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("DELETE FROM reservations WHERE id='"+invalidV5+"'");}
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
            try(Connection c=conn(url);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_name='actual_costs' AND column_name='amount'")){
                assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(19);assertThat(r.getInt(2)).isEqualTo(4);
            }
            stockMovementCompatibilityViewProbe(url);
            campaignMaterialRemovalSqlStateProbe(url);
            try(Connection c=conn(url);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name IN ('uq_wo_spare_req_owner','fk_reservation_requirement_owner')")){
                assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(2);
            }
            reservationTransitionLockProbe(url);
        }finally{try(Connection c=conn(admin);Statement s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+db+" WITH (FORCE)");}}
    }
    private void campaignMaterialRemovalSqlStateProbe(String url)throws Exception{
        UUID campaignRequirement=UUID.randomUUID(),workRequirement=UUID.randomUUID();
        try(Connection c=conn(url);Statement s=c.createStatement()){
            s.execute("SET session_replication_role=replica");
            s.execute("INSERT INTO repair_campaign_material_requirements(id,repair_campaign_id,work_item_id,spare_part_id,warehouse_id,required_quantity,critical,procurement_required,is_deleted,created_at,updated_at) VALUES ('"+campaignRequirement+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',1,false,false,false,now(),now())");
            s.execute("INSERT INTO work_order_spare_part_requirements(id,work_order_id,source_type,campaign_requirement_id,spare_part_id,warehouse_id,required_qty,unit,status,is_deleted,created_at,updated_at) SELECT '"+workRequirement+"','"+UUID.randomUUID()+"','REPAIR_CAMPAIGN_WORK_ITEM',id,spare_part_id,warehouse_id,1,'pcs','PLANNED',false,now(),now() FROM repair_campaign_material_requirements WHERE id='"+campaignRequirement+"'");
            s.execute("SET session_replication_role=origin");
            try{s.execute("UPDATE repair_campaign_material_requirements SET is_deleted=true WHERE id='"+campaignRequirement+"'");throw new AssertionError("expected removal guard");}
            catch(SQLException e){assertThat(e.getSQLState()).isEqualTo("23514");assertThat(e.getMessage()).contains("RC_MATERIAL_REQUIREMENT_IN_USE");assertThat(((org.postgresql.util.PSQLException)e).getServerErrorMessage().getConstraint()).isEqualTo("RC_MATERIAL_REQUIREMENT_IN_USE");}
        }
    }
    private enum LegacyNegative{
        STOCK_METADATA("warehouse_stock_ledger_metadata","submitted_quantity"){
            void insert(String url)throws Exception{sql(url,"INSERT INTO warehouse_stock_ledger_metadata(id,warehouse_id,spare_part_id,legacy_type,submitted_quantity,is_deleted,created_at,updated_at,submitted_occurred_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','ISSUE',-1,false,now(),now(),now())");}},
        MATERIAL_USAGE("repair_material_usages","quantity"){
            void insert(String url)throws Exception{replicaSql(url,"INSERT INTO repair_material_usages(id,work_order_id,warehouse_id,spare_part_id,quantity,is_deleted,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',-1,false,now(),now())");}},
        MATERIAL_RETURN("repair_material_returns","quantity"){
            void insert(String url)throws Exception{replicaSql(url,"INSERT INTO repair_material_returns(id,work_order_id,warehouse_id,spare_part_id,stock_status,quantity,reason,status,is_deleted,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','AVAILABLE',-1,'test','POSTED',false,now(),now())");}},
        TEMPLATE_REQUIREMENT("maintenance_template_spare_part_requirements","quantity"){
            void insert(String url)throws Exception{sql(url,"ALTER TABLE maintenance_template_spare_part_requirements DROP CONSTRAINT chk_mt_spare_req_quantity");replicaSql(url,"INSERT INTO maintenance_template_spare_part_requirements(id,template_id,spare_part_id,quantity,unit,is_active,is_deleted,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',-1,'pcs',true,false,now(),now())");}
            void cleanup(String url)throws Exception{super.cleanup(url);sql(url,"ALTER TABLE maintenance_template_spare_part_requirements ADD CONSTRAINT chk_mt_spare_req_quantity CHECK (quantity > 0)");}},
        REGULATION_REQUIREMENT("maintenance_regulation_spare_part_requirements","quantity"){
            void insert(String url)throws Exception{sql(url,"ALTER TABLE maintenance_regulation_spare_part_requirements DROP CONSTRAINT chk_mr_spare_req_quantity");replicaSql(url,"INSERT INTO maintenance_regulation_spare_part_requirements(id,regulation_id,spare_part_id,quantity,unit,is_active,is_deleted,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',-1,'pcs',true,false,now(),now())");}
            void cleanup(String url)throws Exception{super.cleanup(url);sql(url,"ALTER TABLE maintenance_regulation_spare_part_requirements ADD CONSTRAINT chk_mr_spare_req_quantity CHECK (quantity > 0)");}},
        ACTUAL_COST("actual_costs","amount"){
            void insert(String url)throws Exception{replicaSql(url,"INSERT INTO actual_costs(id,cost_category_id,amount,status,cost_date,is_deleted,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',-1,'PENDING',now(),false,now(),now())");}};
        final String table,column;LegacyNegative(String table,String column){this.table=table;this.column=column;}abstract void insert(String url)throws Exception;
        void cleanup(String url)throws Exception{sql(url,"DELETE FROM "+table+" WHERE "+column+" < 0");}
        static void sql(String url,String sql)throws Exception{try(Connection c=conn(url);Statement s=c.createStatement()){s.execute(sql);}}
        static void replicaSql(String url,String sql)throws Exception{try(Connection c=conn(url);Statement s=c.createStatement()){s.execute("SET session_replication_role=replica");s.execute(sql);}}
    }
    private void stockMovementCompatibilityViewProbe(String url)throws Exception{
        UUID id=UUID.randomUUID(),warehouseId=UUID.randomUUID(),sparePartId=UUID.randomUUID();
        try(Connection c=conn(url);Statement s=c.createStatement()){
            s.execute("INSERT INTO stock_movements(id,warehouse_id,spare_part_id,quantity,type,is_deleted,created_at,updated_at,occurred_at,stock_status) VALUES ('"+id+"','"+warehouseId+"','"+sparePartId+"',1.2500,'ISSUE',false,now(),now(),now(),'AVAILABLE')");
            try(ResultSet r=s.executeQuery("SELECT submitted_quantity FROM warehouse_stock_ledger_metadata WHERE id='"+id+"'")){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("1.2500");}
            assertThatThrownBy(()->s.execute("UPDATE stock_movements SET quantity=2 WHERE id='"+id+"'"))
                    .hasMessageContaining("STOCK_MOVEMENTS_APPEND_ONLY");
            assertThatThrownBy(()->s.execute("DELETE FROM stock_movements WHERE id='"+id+"'"))
                    .hasMessageContaining("STOCK_MOVEMENTS_APPEND_ONLY");
            try(ResultSet r=s.executeQuery("SELECT obj_description('stock_movements'::regclass,'pg_class')")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).contains("Deprecated compatibility view");}
        }
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
