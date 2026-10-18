package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.model.dto.DataProfileResponseDTO;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataProfileToolTest {

    @Mock PythonServiceClient python;
    @InjectMocks DataProfileTool tool;

    @Test
    void singlePythonCallIncludesChartType() {
        DataProfileResponseDTO dto = new DataProfileResponseDTO();
        dto.setChartType("bar");
        dto.setRowCount(3);
        when(python.profileData(any())).thenReturn(dto);
        ToolResult result = tool.execute(new ToolContext(2L, 9L, "q"), Map.of("rows", List.of()));
        assertEquals("bar", result.data().get("chartType"));
        assertEquals(ToolNames.DATA_PROFILE, tool.spec().name());
        verify(python, times(1)).profileData(any());
    }
}
