package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.knowledge.KnowledgeArticle;
import com.resolveiq.knowledge.KnowledgeService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Self-help deflection: shows the knowledge-base articles most similar to the problem, with their
 * fix inline. "This fixed it" ends the conversation without a ticket and counts as a deflection for
 * that article; otherwise the conversation carries on to a normal report.
 */
@Component
class SelfHelpState extends AbstractConversationState {

    static final String FIXED = "FIXED:";
    static final String NOT_FIXED = "NOT_FIXED";

    private final DraftInsight insight;
    private final KnowledgeService knowledge;
    private final Clock clock;

    SelfHelpState(DraftInsight insight, KnowledgeService knowledge, Clock clock) {
        this.insight = insight;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.OFFER_SELF_HELP;
    }

    @Override
    public boolean applies(ChatSession session) {
        return !session.getDraft().isSelfHelpChecked() && !insight.selfHelp(session.getDraft()).isEmpty();
    }

    @Override
    public Prompt prompt(ChatSession session) {
        List<KnowledgeArticle> articles = articles(session);
        Prompt.Builder b = Prompt.ask(articles.size() == 1
                ? "Before I log this: this guide has fixed the same problem for other people. Could you try it?"
                : "Before I log this: these guides have fixed similar problems for other people. Could you try them?");
        b.articles(articles.stream().map(a -> new Prompt.ArticleCard(a.getId(), a.getTitle(), a.getSymptoms(),
                a.getSolution(), a.getDeflections())).toList());
        if (articles.size() == 1) {
            b.option(FIXED + articles.getFirst().getId(), "This fixed it");
        } else {
            articles.forEach(a -> b.option(FIXED + a.getId(), "Fixed by: " + a.getTitle()));
        }
        return b.option(NOT_FIXED, articles.size() == 1 ? "It didn't help" : "None of these helped").build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        List<KnowledgeArticle> articles = articles(session);
        Optional<Boolean> yesNo = AnswerInterpreter.yesNo(input.text());
        boolean typed = input.choice() == null;
        if (input.chose(NOT_FIXED) || (typed && yesNo.isPresent() && !yesNo.get())) {
            session.getDraft().markSelfHelpChecked();
            return StepResult.next("No problem - let's get this to the IT team.");
        }
        Optional<KnowledgeArticle> fixedBy = Optional.empty();
        if (input.choice() != null && input.choice().toUpperCase().startsWith(FIXED)) {
            String id = input.choice().substring(FIXED.length());
            // only an article that was actually offered in this conversation can be credited
            fixedBy = articles.stream().filter(a -> String.valueOf(a.getId()).equals(id)).findFirst();
        } else if (typed && yesNo.orElse(false) && articles.size() == 1) {
            fixedBy = Optional.of(articles.getFirst());
        } else if (typed && yesNo.orElse(false)) {
            return StepResult.notUnderstood("Great - which guide fixed it? Please pick it below.");
        }
        if (fixedBy.isEmpty()) {
            return StepResult.notUnderstood(PICK_ONE);
        }
        KnowledgeArticle article = fixedBy.get();
        knowledge.recordDeflection(article.getId(), session.getOwner());
        session.finishDeflected(article.getId(), clock.instant());
        return StepResult.finished("Glad that sorted it. I haven't created a ticket. If the problem comes back, "
                + "just start a new conversation and I'll log it straight away.");
    }

    private List<KnowledgeArticle> articles(ChatSession session) {
        return insight.selfHelp(session.getDraft()).stream().map(KnowledgeService.Scored::article).toList();
    }
}
