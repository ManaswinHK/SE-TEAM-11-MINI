package com.aas.survey;

import com.aas.auth.CurrentUser;
import com.aas.survey.SurveyService.InviteStatus;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    /** Whether each separation's survey is waiting, sent or answered. Never includes the token or the answers. */
    @GetMapping("/invites")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public List<InviteStatus> invites() {
        return service.statuses(CurrentUser.get());
    }
}
