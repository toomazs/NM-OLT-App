package utils;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.WString;

public class WindowsUtils {

    public static void setAppUserModelId(final String appId) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Shell32 INSTANCE = Native.load("shell32", Shell32.class, W32APIOptions.DEFAULT_OPTIONS);
                WString wAppId = new WString(appId);
                INSTANCE.SetCurrentProcessExplicitAppUserModelID(wAppId);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                System.err.println("Failed to set AppUserModelID using JNA: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error setting AppUserModelID: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("AppUserModelID not set (not running on Windows).");
        }
    }
}