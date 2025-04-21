package config;
import java.util.*;

/**
 * 权限配置类，用于定义API接口的访问权限
 */
public class PermissionConfig {
    // key = METHOD + ":" + 路由模板（与 Application 中一致）
    public static final Map<String, Set<String>> PERMISSIONS = new HashMap<>();
    
    // 创建不可变集合的辅助方法
    private static Set<String> setOf(String... values) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, values);
        return Collections.unmodifiableSet(set);
    }
    
    static {
        // 数据库管理权限
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/create", setOf("admin"));
        PERMISSIONS.put("DELETE:/db/data/database/{databaseName}", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/start", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/backup", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/restore", setOf("admin"));
        
        // 读取权限
        PERMISSIONS.put("GET:/db/data/labels", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/nodes", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/nodes/paginated", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/relationships/paginated", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/{id}", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/types", setOf("reader", "admin"));
        PERMISSIONS.put("GET:/db/data/label/{labelName}/nodes", setOf("reader", "admin"));
        
        // 写入权限
        PERMISSIONS.put("POST:/db/data/node", setOf("writer", "admin"));
        PERMISSIONS.put("POST:/db/data/node/{id}/relationships", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("POST:/db/data/node/{id}/labels", setOf("writer", "admin"));
        
        // 删除权限
        PERMISSIONS.put("DELETE:/db/data/node/{id}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/labels/{labelName}", setOf("writer", "admin"));
        
        // 时态属性权限
        PERMISSIONS.put("GET:/db/data/node/{id}/temporal/{key}/{time}", setOf("reader", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/temporal/{key}/{time}", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/temporal/{key}/{startTime}/{endTime}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/temporal/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/{id}/temporal/{key}/{time}", setOf("reader", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/temporal/{key}/{time}", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/temporal/{key}/{startTime}/{endTime}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/temporal/{key}", setOf("writer", "admin"));
        
        // 系统监控权限
        PERMISSIONS.put("GET:/system/resources", setOf("admin"));
        PERMISSIONS.put("GET:/system/threads", setOf("admin"));
        PERMISSIONS.put("GET:/admin/active-requests", setOf("admin"));
        PERMISSIONS.put("GET:/user/logs", setOf("admin"));
        PERMISSIONS.put("GET:/user/list", setOf("admin"));
    }
} 