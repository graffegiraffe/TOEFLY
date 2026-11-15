package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.api.SpeechApi;
import com.toefly.aigateway.adapter.in.web.dto.AssessedPhoneme;
import com.toefly.aigateway.adapter.in.web.dto.PronunciationRequest;
import com.toefly.aigateway.adapter.in.web.dto.PronunciationResponse;
import com.toefly.aigateway.adapter.in.web.dto.SynthesisRequest;
import com.toefly.aigateway.adapter.in.web.dto.TranscribedWord;
import com.toefly.aigateway.adapter.in.web.dto.TranscriptionRequest;
import com.toefly.aigateway.adapter.in.web.dto.TranscriptionResponse;
import com.toefly.aigateway.application.port.in.SpeechUseCase;
import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.AssessmentRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Transcription, pronunciation assessment and synthesis for speech-service. */
@RestController
public class SpeechController implements SpeechApi {

    private final SpeechUseCase speech;

    public SpeechController(SpeechUseCase speech) {
        this.speech = speech;
    }

    @Override
    public ResponseEntity<TranscriptionResponse> transcribe(TranscriptionRequest request) {
        var result = speech.transcribe(new SpeechUseCase.TranscribeCommand(
                Callers.service(),
                request.getAudioUrl(),
                request.getLanguage(),
                request.getPrompt(),
                request.getUserId(),
                request.getFeature()));
        return ResponseEntity.ok(new TranscriptionResponse()
                .text(result.transcript().text())
                .words(result.transcript().words().stream()
                        .map(w -> new TranscribedWord()
                                .w(w.word())
                                .start(w.start())
                                .end(w.end())
                                .p(w.probability()))
                        .toList())
                .durationSec(result.transcript().durationSec())
                .aiStamp(AiDtoMapper.stamp(result.aiStamp())));
    }

    @Override
    public ResponseEntity<PronunciationResponse> assessPronunciation(PronunciationRequest request) {
        boolean scripted = request.getMode() == PronunciationRequest.ModeEnum.SCRIPTED;
        var result = speech.assess(new SpeechUseCase.AssessCommand(
                Callers.service(),
                request.getAudioUrl(),
                new AssessmentRequest(
                        scripted,
                        request.getReferenceText(),
                        request.getLocale().getValue(),
                        Boolean.TRUE.equals(request.getEnableProsody()),
                        Boolean.TRUE.equals(request.getEnableMiscue()),
                        request.getGranularity() == PronunciationRequest.GranularityEnum.WORD ? "Word" : "Phoneme"),
                request.getUserId(),
                request.getFeature()));
        Assessment a = result.assessment();
        return ResponseEntity.ok(new PronunciationResponse()
                .accuracy(a.accuracy())
                .fluency(a.fluency())
                .completeness(a.completeness())
                .prosody(a.prosody())
                .pronScore(a.pronScore())
                .recognizedText(a.recognizedText())
                .words(a.words().stream()
                        .map(w -> new com.toefly.aigateway.adapter.in.web.dto.AssessedWord()
                                .word(w.word())
                                .accuracy(w.accuracy())
                                .errorType(w.errorType())
                                .offsetMs(w.offsetMs())
                                .durationMs(w.durationMs())
                                .phonemes(w.phonemes().stream()
                                        .map(p -> new AssessedPhoneme()
                                                .p(p.phoneme())
                                                .accuracy(p.accuracy()))
                                        .toList()))
                        .toList())
                .aiStamp(AiDtoMapper.stamp(result.aiStamp())));
    }

    @Override
    public ResponseEntity<Resource> synthesizeSpeech(SynthesisRequest request) {
        var result = speech.synthesize(new SpeechUseCase.SynthesizeCommand(
                Callers.service(),
                request.getSsml(),
                request.getText(),
                request.getVoice(),
                request.getFormat(),
                request.getUserId()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("X-Toefly-Ai-Call-Id", result.aiStamp().aiCallId())
                .header("X-Toefly-Usage-Characters", Integer.toString(result.characters()))
                .header("X-Toefly-Cache-Hit", Boolean.toString(result.aiStamp().cacheHit()))
                .body(new ByteArrayResource(result.audio()));
    }
}
