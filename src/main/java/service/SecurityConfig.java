package service;

public class SecurityConfig {
    private static boolean authEnabled = true;
    
    public static boolean isAuthEnabled() {
        return authEnabled;
    }
    
    public static void setAuthEnabled(boolean enabled) {
        authEnabled = enabled;
    }
} 