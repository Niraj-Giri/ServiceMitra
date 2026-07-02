package com.mitra.provider;

import com.mitra.entity.ProviderProfile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderProfileController {

    private final ProviderProfileService service;

    public ProviderProfileController(ProviderProfileService service) {
        this.service = service;
    }

}
