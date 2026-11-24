package com.example.httpreading.api;

import com.example.httpreading.common.web.CommonResponse;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ManualStyleProfileRequest;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ManualStyleProfileResponse;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ProfileOverviewResponse;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ProfileUpdateResponse;
import com.example.httpreading.modules.profile.service.ProfileUpdateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileUpdateService profileUpdateService;

    public ProfileController(ProfileUpdateService profileUpdateService) {
        this.profileUpdateService = profileUpdateService;
    }

    @PostMapping("/update")
    public CommonResponse<ProfileUpdateResponse> update(@RequestBody(required = false) ProfileUpdateRequest request) {
        return CommonResponse.success(profileUpdateService.updateProfileManually(
            request == null ? new ProfileUpdateRequest(null, null, null, null, null, null) : request));
    }

    @GetMapping
    public CommonResponse<ProfileOverviewResponse> overview(@RequestParam(required = false) String userId,
                                                            @RequestParam(required = false) String sessionId) {
        return CommonResponse.success(profileUpdateService.overview(userId, sessionId));
    }

    @PutMapping("/style")
    public CommonResponse<ManualStyleProfileResponse> updateStyle(@RequestBody(required = false) ManualStyleProfileRequest request) {
        return CommonResponse.success(profileUpdateService.updateStyleManually(request));
    }
}
