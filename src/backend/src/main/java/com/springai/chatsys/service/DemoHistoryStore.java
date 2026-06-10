package com.springai.chatsys.service;

import com.springai.chatsys.dto.KnowledgeRecordDTO;
import com.springai.chatsys.dto.QuestionRecordDTO;

import java.util.List;

public interface DemoHistoryStore {

    void recordKnowledge(KnowledgeRecordDTO record);

    void recordQuestion(QuestionRecordDTO record);

    List<KnowledgeRecordDTO> knowledgeForUser(String userId);

    List<QuestionRecordDTO> questionsForUser(String userId);

    int clearKnowledgeForUser(String userId);

    int clearQuestionsForUser(String userId);
}
