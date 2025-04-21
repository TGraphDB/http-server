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
        // 公共API - 无需权限验证，在accessManager处理，不必在这里配置
        // "/user/login"
        // "/user/register"
        // "/system/resources" (默认公开，如需控制权限，可在此配置)
        // "/"

        // ========================= 数据库管理权限 =========================
        // 创建、启动、备份等数据库管理操作
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/create", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/start", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database", setOf("admin")); // 关闭数据库
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/backup", setOf("admin"));
        PERMISSIONS.put("POST:/db/data/database/{databaseName}/restore", setOf("admin"));
        PERMISSIONS.put("DELETE:/db/data/database/{databaseName}", setOf("admin"));
        
        // 数据库状态和元信息操作
        PERMISSIONS.put("GET:/db/data/database/backup", setOf("admin"));
        PERMISSIONS.put("GET:/db/data/database/{databaseName}/path", setOf("admin"));
        PERMISSIONS.put("GET:/db/data/database/{databaseName}/status", setOf("admin"));
        PERMISSIONS.put("GET:/databases/{dbname}/space", setOf("admin"));
        PERMISSIONS.put("GET:/db/data/databases", setOf("reader", "writer", "admin")); // 列出数据库

        // ========================= 读取操作权限 =========================
        // 基础数据获取
        PERMISSIONS.put("GET:/db/data/", setOf("reader", "writer", "admin")); // 数据根节点
        PERMISSIONS.put("GET:/db/data/labels", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/nodes", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/nodes/paginated", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationships/paginated", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/types", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/propertykeys", setOf("reader", "writer", "admin"));
        
        // 节点相关查询
        PERMISSIONS.put("GET:/db/data/node/{id}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/properties", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/properties/{key}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/labels", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/degree/*", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/label/{labelName}/nodes", setOf("reader", "writer", "admin"));
        
        // 关系相关查询
        PERMISSIONS.put("GET:/db/data/relationship/{id}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/{id}/properties", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationship/{id}/properties/{key}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/relationships/all", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/relationships/in", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/relationships/out", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/node/{id}/relationships/all/{typeString}", setOf("reader", "writer", "admin"));
        
        // 统计相关
        PERMISSIONS.put("GET:/db/data/nodes/count", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("GET:/db/data/relationships/count", setOf("reader", "writer", "admin"));

        // ========================= 写入操作权限 =========================
        // 节点创建修改
        PERMISSIONS.put("POST:/db/data/node", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("POST:/db/data/node/{id}/labels", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/labels", setOf("writer", "admin")); // 替换标签
        
        // 关系创建修改
        PERMISSIONS.put("POST:/db/data/node/{id}/relationships", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/properties/{key}", setOf("writer", "admin"));

        // ========================= 删除操作权限 =========================
        // 节点删除
        PERMISSIONS.put("DELETE:/db/data/node/{id}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/properties/{key}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/labels/{labelName}", setOf("writer", "admin"));
        
        // 关系删除
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/properties", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/properties/{key}", setOf("writer", "admin"));

        // ========================= 时态属性权限 =========================
        // 节点时态属性
        PERMISSIONS.put("GET:/db/data/node/{id}/temporal/{key}/{time}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/temporal/{key}/{time}", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/node/{id}/temporal/{key}/{startTime}/{endTime}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/node/{id}/temporal/{key}", setOf("writer", "admin"));
        
        // 关系时态属性
        PERMISSIONS.put("GET:/db/data/relationship/{id}/temporal/{key}/{time}", setOf("reader", "writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/temporal/{key}/{time}", setOf("writer", "admin"));
        PERMISSIONS.put("PUT:/db/data/relationship/{id}/temporal/{key}/{startTime}/{endTime}", setOf("writer", "admin"));
        PERMISSIONS.put("DELETE:/db/data/relationship/{id}/temporal/{key}", setOf("writer", "admin"));

        // ========================= 系统管理权限 =========================
        // 系统和用户管理
        PERMISSIONS.put("GET:/system/resources", setOf("admin")); // 可选：设为公开或限制权限
        PERMISSIONS.put("GET:/system/threads", setOf("admin"));
        PERMISSIONS.put("GET:/admin/active-requests", setOf("admin"));
        PERMISSIONS.put("GET:/user/logs", setOf("admin"));
        PERMISSIONS.put("GET:/user/list", setOf("admin"));
        
        // ========================= 用户操作权限 =========================
        // 用户自身操作
        PERMISSIONS.put("GET:/user/{username}/status", setOf("reader", "writer", "admin")); // 只能查看自己的状态
        PERMISSIONS.put("POST:/user/{username}/password", setOf("reader", "writer", "admin")); // 只能修改自己的密码
        PERMISSIONS.put("POST:/user/logout", setOf("reader", "writer", "admin")); // 所有登录用户都可登出
    }
} 