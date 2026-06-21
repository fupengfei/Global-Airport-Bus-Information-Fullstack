package com.airportbus.admin.api;

import com.airportbus.admin.api.dto.CreateBusRequest;
import com.airportbus.admin.api.dto.UpdateBusRequest;
import com.airportbus.audit.Audited;
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-bus", description = "巴士维护(管理员)")
@RestController
@RequestMapping("/api/v1/admin/buses")
public class AdminBusController {

    private final BusCommandService cmd;
    private final BusWriteMapper writeMapper;
    private final BusVersionMapper versionMapper;

    public AdminBusController(BusCommandService cmd, BusWriteMapper writeMapper, BusVersionMapper versionMapper) {
        this.cmd = cmd; this.writeMapper = writeMapper; this.versionMapper = versionMapper;
    }

    @GetMapping("/tree")
    public List<BusWriteMapper.AdminTreeRow> tree() {
        CurrentUser.requireAdmin();
        return writeMapper.selectAdminTree();
    }

    @GetMapping("/{sourceId}")
    public BusView get(@PathVariable String sourceId) {
        CurrentUser.requireAdmin();
        if (writeMapper.selectVersionHash(sourceId) == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        return cmd.viewFor(sourceId);
    }

    @PostMapping
    @Audited(action = "CREATE_BUS")
    public BusView create(@RequestBody CreateBusRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return cmd.save(req.sourceId(), resolveAirport(req.airportCode()), req.data(), null, actor(me), false);
    }

    @PutMapping("/{sourceId}")
    @Audited(action = "UPDATE_BUS")
    public BusView update(@PathVariable String sourceId, @RequestBody UpdateBusRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return cmd.save(sourceId, resolveAirport(req.airportCode()), req.data(), req.version(), actor(me), false);
    }

    @PostMapping("/{sourceId}/verify")
    @Audited(action = "VERIFY_BUS")
    public void verify(@PathVariable String sourceId) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        cmd.verify(sourceId, actor(me));
    }

    @DeleteMapping("/{sourceId}")
    @Audited(action = "DELETE_BUS")
    public void delete(@PathVariable String sourceId) {
        JwtPrincipal me = CurrentUser.requireSuperAdmin();
        cmd.delete(sourceId, actor(me));
    }

    @GetMapping("/{sourceId}/versions")
    public List<BusVersionMapper.Meta> versions(@PathVariable String sourceId) {
        CurrentUser.requireAdmin();
        return versionMapper.listVersions(busId(sourceId));
    }

    @GetMapping("/{sourceId}/versions/{version}")
    public com.airportbus.bus.api.dto.BusInput versionSnapshot(@PathVariable String sourceId, @PathVariable int version) {
        CurrentUser.requireAdmin();
        return cmd.getVersion(sourceId, version);
    }

    @PostMapping("/{sourceId}/versions/{version}/rollback")
    @Audited(action = "ROLLBACK_BUS")
    public BusView rollback(@PathVariable String sourceId, @PathVariable int version) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return cmd.rollback(sourceId, version, actor(me));
    }

    private long resolveAirport(String code) {
        Long id = writeMapper.findAirportId(code);
        if (id == null) throw new ApiException(ErrorCode.AIRPORT_NOT_FOUND, code);
        return id;
    }
    private long busId(String sourceId) {
        Long id = writeMapper.findBusId(sourceId);
        if (id == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        return id;
    }
    private static String actor(JwtPrincipal me) { return "admin:" + me.userId(); }
}
