package com.tradej.app.config;

import com.tradej.core.infrastructure.WorkspacePaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class WorkspaceConfiguration {

    @Bean
    WorkspacePaths workspacePaths() {
        return WorkspacePaths.fromSystemProperty();
    }

    @Bean
    Path workspaceRoot(WorkspacePaths workspacePaths) {
        return workspacePaths.workspaceRoot();
    }
}
