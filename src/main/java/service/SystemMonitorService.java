package service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统监控服务，提供CPU和内存使用情况
 */
public class SystemMonitorService {
    
    private final Runtime runtime = Runtime.getRuntime();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    /**
     * 获取系统资源使用情况
     * @return 包含CPU和内存使用情况的Map
     */
    public Map<String, Object> getSystemResources() {
        Map<String, Object> resources = new HashMap<>();
        
        // 获取内存使用情况（以字节为单位）
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // 获取CPU使用情况
        double processCpuLoad = 0;
        double systemCpuLoad = 0;
        
        // 尝试获取CPU负载
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            processCpuLoad = sunOsBean.getProcessCpuLoad();
            systemCpuLoad = sunOsBean.getSystemCpuLoad();
        }
        
        // 内存信息 (MB)
        resources.put("totalMemory", totalMemory / (1024 * 1024) + " MB");
        resources.put("usedMemory", usedMemory / (1024 * 1024) + " MB");
        resources.put("freeMemory", freeMemory / (1024 * 1024) + " MB");
        
        // CPU信息
        resources.put("processCpuLoad", String.format("%.2f%%", processCpuLoad * 100));
        resources.put("systemCpuLoad", String.format("%.2f%%", systemCpuLoad * 100));
        resources.put("availableProcessors", runtime.availableProcessors() + " 核");
        
        return resources;
    }
    
    /**
     * 获取线程信息，特别是qtp开头的线程及其状态
     * @return 包含线程信息的Map
     */
    public Map<String, Object> getThreadInfo() {
        Map<String, Object> threadInfo = new HashMap<>();
        Map<String, Integer> qtpThreadStates = new ConcurrentHashMap<>();
        
        // 初始化所有可能的线程状态计数
        for (Thread.State state : Thread.State.values()) {
            qtpThreadStates.put(state.name(), 0);
        }
        
        int qtpThreadCount = 0;
        
        // 获取所有线程ID
        long[] threadIds = threadBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
        
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                String threadName = info.getThreadName();
                
                // 检查是否是qtp开头的线程
                if (threadName != null && threadName.startsWith("qtp")) {
                    qtpThreadCount++;
                    
                    // 获取线程状态并增加计数
                    Thread.State state = info.getThreadState();
                    qtpThreadStates.put(state.name(), qtpThreadStates.get(state.name()) + 1);
                }
            }
        }
        
        // 添加总qtp线程数
        threadInfo.put("qtpThreadCount", qtpThreadCount);
        
        // 添加各状态的线程数
        Map<String, Integer> stateCountMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : qtpThreadStates.entrySet()) {
            if (entry.getValue() > 0) {
                stateCountMap.put(entry.getKey(), entry.getValue());
            }
        }
        threadInfo.put("qtpThreadStates", stateCountMap);
        
        return threadInfo;
    }
}