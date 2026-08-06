package com.polarcode.render.inline;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

/**
 * 终端能力探测：决定 inline 渲染器的各项特性是否可启用。
 *
 * <p>探测逻辑保守——能开则开，老终端 / 非 TTY 环境优雅降级。
 */
public final class TerminalCapabilities {

    private TerminalCapabilities() {
    }

    /** 终端是否能渲染 ANSI 转义序列（颜色、光标控制、inline status 等）。 */
    public static boolean supportsAnsi(Terminal terminal) {
        if (terminal == null) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            // NO_COLOR 只影响样式，不影响光标控制——保留 true，颜色由 AnsiStyle 自己关
            return true;
        }
        // JLine 在 Windows + PowerShell 组合下经常把 modern terminal 探测成 dumb，
        // 但 Windows Terminal / PowerShell 7 / 现代 cmd 实际都支持 ANSI。
        // 用 stdout 是否是 TTY 做兜底判断：非 TTY 才认为是 dumb。
        if (Boolean.parseBoolean(System.getProperty("polarcode.force.plain"))
                || Boolean.parseBoolean(System.getenv("POLARCODE_FORCE_PLAIN"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("polarcode.force.ansi"))
                || Boolean.parseBoolean(System.getenv("POLARCODE_FORCE_ANSI"))) {
            return true;
        }
        String type = terminal.getType();
        if (type != null && type.equalsIgnoreCase("dumb")) {
            // JLine 报告 dumb 时，再检查 stdout 是不是 TTY。
            // Windows Terminal / PowerShell 7 即使被 JLine 标 dumb，实际也支持 ANSI。
            return isStdoutTty();
        }
        return true;
    }

    private static boolean isStdoutTty() {
        // System.console() 在 stdin/stdout 不是 TTY（重定向、管道）时返回 null。
        // Windows Terminal + PowerShell 下通常是 TTY。
        return System.console() != null;
    }

    /**
     * 终端是否适合启用 inline status 状态区。
     * 同时校验终端尺寸合理（rows ≥ 5）。
     */
    public static boolean supportsScrollRegion(Terminal terminal) {
        if (!supportsAnsi(terminal)) {
            return false;
        }
        if (Boolean.parseBoolean(System.getenv("POLARCODE_NO_STATUSBAR"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("polarcode.no.statusbar"))) {
            return false;
        }
        Size size = safeSize(terminal);
        return size.getRows() >= 5 && size.getColumns() >= 20;
    }

    /** 终端是否支持 24-bit TrueColor（用于丰富的代码高亮等）。 */
    public static boolean supportsTrueColor() {
        String colorterm = System.getenv("COLORTERM");
        return "truecolor".equalsIgnoreCase(colorterm) || "24bit".equalsIgnoreCase(colorterm);
    }

    public static Size safeSize(Terminal terminal) {
        try {
            Size s = terminal.getSize();
            if (s == null || s.getRows() <= 0 || s.getColumns() <= 0) {
                return new Size(80, 24);
            }
            return s;
        } catch (Exception e) {
            return new Size(80, 24);
        }
    }
}
