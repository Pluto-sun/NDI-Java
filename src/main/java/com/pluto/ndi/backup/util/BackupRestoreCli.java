package com.pluto.ndi.backup.util;

import com.fasterxml.jackson.databind.*;
import com.pluto.ndi.backup.config.RestoreTarget;
import com.pluto.ndi.backup.domain.RestoreFailure;
import com.pluto.ndi.backup.service.BackupRestoreService;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;

/** 通过独立主类运行，不启动 Spring、Flyway、Web 或业务定时任务。 */
public final class BackupRestoreCli {
    private BackupRestoreCli() { }
    public static void main(String[] args) {System.exit(run(args,System.getenv(),System.out,System.err));}
    public static int run(String[] args,Map<String,String> environment,PrintStream output,PrintStream errors) {
        if(args.length==1 && args[0].equals("--help")) {usage(output);return 0;}
        try {
            if(args.length<1 || !Set.of("verify","restore").contains(args[0])) throw invalid();
            var options=new HashMap<String,String>();
            for(int index=1;index<args.length;index+=2) {
                if(index+1>=args.length || !Set.of("--archive","--sha256","--max-bytes","--confirm-empty-target","--operator").contains(args[index])
                        || options.putIfAbsent(args[index],args[index+1])!=null) throw invalid();
            }
            if(!options.containsKey("--archive") || !options.containsKey("--sha256")) throw invalid();
            var archive=Path.of(options.get("--archive")).toAbsolutePath().normalize();
            String checksum=options.get("--sha256").toLowerCase(Locale.ROOT);
            long maximum=Long.parseLong(options.getOrDefault("--max-bytes","2147483648"));
            if(maximum<1 || !checksum.matches("[0-9a-f]{64}") || !Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)) throw invalid();
            var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            var service=new BackupRestoreService(json);
            if(args[0].equals("verify")) {
                var manifest=service.verify(archive,checksum,maximum);
                output.println(json.writeValueAsString(Map.of("status","VERIFIED","backupId",manifest.backupId(),"tables",manifest.tables().size(),"objects",manifest.objects().size())));
            } else {
                String confirmation=options.get("--confirm-empty-target");String operator=options.get("--operator");
                if(confirmation==null || operator==null || operator.isBlank() || operator.length()>100) throw invalid();
                var target=RestoreTarget.from(environment,confirmation);
                output.println(json.writeValueAsString(service.restore(archive,checksum,maximum,target,operator)));
            }
            return 0;
        } catch(RestoreFailure failure) {
            errors.println("恢复工具未完成："+failure.reason()+"。不会清理或覆盖已有目标数据；若已开始写入，目标可能存在部分恢复结果，请勿启用业务服务。");
            return failure.reason()==RestoreFailure.Reason.TARGET_NOT_EMPTY || failure.reason()==RestoreFailure.Reason.TARGET_BUSY?3:failure.reason()==RestoreFailure.Reason.RESTORE_FAILED?4:2;
        } catch(Exception failure) {errors.println("恢复工具参数或归档无效，未生成成功报告。");return 2;}
    }
    private static RestoreFailure invalid() {return new RestoreFailure(RestoreFailure.Reason.CONFIG_INVALID);}
    private static void usage(PrintStream output) {
        output.println("用法：restore-backup.sh verify --archive 文件 --sha256 受信任摘要 [--max-bytes 字节数]");
        output.println("或：restore-backup.sh restore --archive 文件 --sha256 受信任摘要 --confirm-empty-target 目标库名 --operator 操作人");
        output.println("恢复专用环境变量：NDI_RESTORE_JDBC_URL、NDI_RESTORE_DB_USERNAME、NDI_RESTORE_DB_PASSWORD、NDI_RESTORE_MINIO_ENDPOINT、NDI_RESTORE_MINIO_ACCESS_KEY、NDI_RESTORE_MINIO_SECRET_KEY。");
        output.println("只允许独立空数据库和空对象存储实例，不提供强制覆盖；成功报告前不得启用目标业务服务。");
    }
}
