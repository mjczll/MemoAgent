package com.memoagent.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 日记保存后的经验提取触发器。
 * <p>
 * AFTER_COMMIT 保证只对真正落库的日记提取；@Async 把它挪到独立线程，
 * 因为一次提取就是一次完整的模型调用，放在保存请求里会让接口卡好几秒。
 * 两个注解的顺序不影响语义：事务提交后触发，触发后切线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryCandidateListener {

    private final DiaryCandidateService diaryCandidateService;

    @Async("diaryAiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDiaryCreated(DiaryCreatedEvent event) {
        diaryCandidateService.generate(event.diaryId());
    }
}
