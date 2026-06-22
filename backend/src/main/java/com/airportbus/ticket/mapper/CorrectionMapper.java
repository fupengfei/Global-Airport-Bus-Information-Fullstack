package com.airportbus.ticket.mapper;

import com.airportbus.ticket.CorrectionReport;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface CorrectionMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: relatedSourceId,description,contact,reporterIp,createdBy。 */
    int insert(Map<String, Object> row);

    CorrectionReport selectById(@Param("id") long id);

    List<CorrectionReport> selectPage(@Param("status") String status,
                                      @Param("limit") int limit, @Param("offset") int offset);

    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("resolutionNote") String resolutionNote, @Param("updatedBy") String updatedBy);
}
