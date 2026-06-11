package com.tradej.mcp.tools;

import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.sync.DataGapScanService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SyncTools {

    private final DataGapScanService gapScanService;
    private final DownloadJobService downloadJobService;

    public SyncTools(
            @Autowired(required = false) DataGapScanService gapScanService,
            @Autowired(required = false) DownloadJobService downloadJobService
    ) {
        this.gapScanService = gapScanService;
        this.downloadJobService = downloadJobService;
    }

    @Tool(name = "get_data_gaps",
          description = "Scan for missing or partial data gaps in the equity parquet warehouse. "
                      + "Returns trading days with missing or incomplete data. "
                      + "Use this before triggering a sync to see what needs to be fetched.")
    public String getDataGaps(
            @ToolParam(description = "Exchange segment: NSE_EQ") String segment,
            @ToolParam(description = "Lookback in months, default 3") Integer lookbackMonths
    ) {
        if (gapScanService == null) {
            return "DataGapScanService not available";
        }
        int months = lookbackMonths != null ? lookbackMonths : 3;
        var report = gapScanService.scan(segment != null ? segment : "NSE_EQ", "1m", months);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Data Gap Scan ===\n");
        sb.append("Range: ").append(report.scanFrom()).append(" to ").append(report.scanTo()).append("\n");
        sb.append("Trading days: ").append(report.totalTradingDays()).append("\n");
        sb.append("Complete: ").append(report.daysComplete()).append("\n");
        sb.append("Partial: ").append(report.daysPartial()).append("\n");
        sb.append("Missing: ").append(report.daysMissing()).append("\n");
        sb.append("Fully complete: ").append(report.isFullyComplete()).append("\n");
        if (!report.missingDates().isEmpty()) {
            sb.append("\nMissing dates:\n");
            report.missingDates().forEach(d -> sb.append("  ").append(d).append("\n"));
        }
        if (!report.partialDates().isEmpty()) {
            sb.append("\nPartial dates:\n");
            report.partialDates().forEach(d -> sb.append("  ").append(d).append("\n"));
        }
        return sb.toString();
    }

    @Tool(name = "get_download_jobs",
          description = "List recent download jobs (equity and options). Shows job ID, type, status, "
                      + "creation time, and completion stats.")
    public String getDownloadJobs(
            @ToolParam(description = "Number of recent jobs to show, default 10") Integer limit
    ) {
        if (downloadJobService == null) {
            return "DownloadJobService not available";
        }
        int rowLimit = limit != null ? limit : 10;
        try {
            var jobs = downloadJobService.listRecentJobs(rowLimit);
            StringBuilder sb = new StringBuilder();
            sb.append("=== Recent Download Jobs ===\n\n");
            for (var job : jobs) {
                sb.append("Job: ").append(job.jobId()).append("\n");
                sb.append("  Type: ").append(job.sourceType()).append("\n");
                sb.append("  Status: ").append(job.status()).append("\n");
                sb.append("  Created: ").append(job.createdAtMs()).append("\n");
                if (job.finishedAtMs() != null) {
                    sb.append("  Finished: ").append(job.finishedAtMs()).append("\n");
                }
                try {
                    var stats = downloadJobService.stats(job.jobId());
                    sb.append("  Tasks: ").append(stats.completedTasks()).append("/")
                      .append(stats.totalTasks()).append(" completed, ")
                      .append(stats.rowsWritten()).append(" rows\n");
                } catch (Exception ignored) {}
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Failed to list download jobs: " + ex.getMessage();
        }
    }
}
