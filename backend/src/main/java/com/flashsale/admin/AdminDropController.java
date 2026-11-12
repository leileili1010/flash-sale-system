package com.flashsale.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (secured by path in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/drops")
public class AdminDropController {

    private final AdminDropService drops;

    AdminDropController(AdminDropService drops) {
        this.drops = drops;
    }

    @GetMapping
    public List<AdminDropView> list() {
        return drops.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDropView create(@Valid @RequestBody CreateDropRequest request) {
        return drops.create(request);
    }

    @PostMapping("/{dropId}/publish")
    public AdminDropView publish(@PathVariable long dropId) {
        return drops.publish(dropId);
    }

    @PostMapping("/{dropId}/cancel")
    public AdminDropView cancel(@PathVariable long dropId) {
        return drops.cancel(dropId);
    }
}
