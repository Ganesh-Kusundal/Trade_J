package com.tradej.cli.interactive;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.Ansi;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Terminal status bar displayed below the REPL prompt.
 *
 * <p>Shows: broker type, profile, connection state, and current time.
 * Updated on each prompt refresh.
 *
 * <p>Example:
 * <pre>
 *  dhan/live  │  ● attached  │  14:32:05
 * </pre>
 */
public final class StatusBar {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CliContext context;

    public StatusBar(CliContext context) {
        this.context = context;
    }

    /**
     * Render the status bar as a string suitable for use as a JLine right prompt
     * or printed below the output.
     */
    public String render() {
        String broker = Ansi.bold(context.brokerType().name().toLowerCase()
                + "/" + context.profile().name().toLowerCase());

        String attach;
        try {
            attach = context.attachReachable()
                    ? Ansi.green("● attached")
                    : Ansi.red("○ detached");
        } catch (Exception e) {
            attach = Ansi.red("○ error");
        }

        String time = Ansi.dim(LocalTime.now().format(TIME_FMT));

        return Ansi.dim(" ") + broker + Ansi.dim(" │ ") + attach + Ansi.dim(" │ ") + time + Ansi.dim(" ");
    }

    /**
     * Render a compact one-line header showing context state.
     * Used in the welcome banner.
     */
    public String renderHeader() {
        String broker = Ansi.cyan(Ansi.bold("Trade-J"));
        String brokerInfo = Ansi.dim("broker: ")
                + Ansi.yellow(context.brokerType().name().toLowerCase()
                + "/" + context.profile().name().toLowerCase());

        String attachInfo;
        try {
            attachInfo = context.attachReachable()
                    ? Ansi.dim("attach: ") + Ansi.green("● " + context.attachUrl())
                    : Ansi.dim("attach: ") + Ansi.red("○ " + context.attachUrl());
        } catch (Exception e) {
            attachInfo = Ansi.dim("attach: ") + Ansi.red("○ error");
        }

        return broker + "  " + brokerInfo + "  " + attachInfo;
    }
}
