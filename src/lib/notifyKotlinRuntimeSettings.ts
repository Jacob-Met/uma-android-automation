import { NativeModules, Platform } from "react-native"
import { logErrorWithTimestamp } from "./logger"

/** Asks the native bot thread to reload settings from SQLite after a React-side save. */
export const notifyKotlinRuntimeSettingsUpdated = (): void => {
    if (Platform.OS !== "android") {
        return
    }
    try {
        NativeModules.StartModule?.notifyRuntimeSettingsUpdated?.()
    } catch (error) {
        logErrorWithTimestamp("[Settings] Failed to notify native runtime settings reload:", error)
    }
}
