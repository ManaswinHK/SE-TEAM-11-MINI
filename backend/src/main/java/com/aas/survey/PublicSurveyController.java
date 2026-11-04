package com.aas.survey;

import com.aas.survey.SurveyService.SurveyAnswers;
import com.aas.survey.SurveyService.SurveyForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The exit survey itself. The link's token is the only credential, so these routes need no login. */
@RestController
@RequestMapping("/api/public/survey")
public class PublicSurveyController {

    private final SurveyService service;

    public PublicSurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public SurveyForm form(@PathVariable String token) {
        return service.form(token);
    }

    @PostMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@PathVariable String token, @Valid @RequestBody SurveyAnswers answers) {
        service.submit(token, answers);
    }
}
