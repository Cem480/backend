package com.ares.backend.service;

import com.ares.backend.model.Survey;
import com.ares.backend.model.UserSession;
import com.ares.backend.dto.ConflictResolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GBCRAlgorithm {

    public ConflictResolution resolveConflict(UserSession session, Survey newDAG) {
        List<String> zombies = detectZombieNodes(session, newDAG);
        log.info("Zombie nodes detected: {}", zombies);

        boolean canRecover = atomicRecover(session, newDAG);

        if (zombies.isEmpty() && canRecover) {
            log.info("Atomic recovery possible for session {}", session.getSessionId());
            return new ConflictResolution(
                    "ATOMIC_RECOVERY",
                    session.getLastStableNode(),
                    zombies,
                    newDAG.getVersion());
        }

        String stableNode = findStableNode(session, newDAG);
        log.info("Rolling back to stable node: {}", stableNode);
        return new ConflictResolution(
                "ROLLBACK",
                stableNode,
                zombies,
                newDAG.getVersion());
    }

    public List<String> detectZombieNodes(UserSession session, Survey newDAG) {
        Set<String> existingIds = newDAG.getNodes().stream()
                .map(n -> n.getId())
                .collect(Collectors.toSet());

        List<String> zombies = new ArrayList<>();

        if (session.getAnswers() != null) {
            for (String id : session.getAnswers().keySet()) {
                if (!existingIds.contains(id))
                    zombies.add(id);
            }
        }

        if (session.getExpectedNextNodes() != null) {
            for (String id : session.getExpectedNextNodes()) {
                if (!existingIds.contains(id))
                    zombies.add(id);
            }
        }

        return zombies.stream().distinct().collect(Collectors.toList());
    }

    public String findStableNode(UserSession session, Survey newDAG) {
        Set<String> existingIds = newDAG.getNodes().stream()
                .map(n -> n.getId())
                .collect(Collectors.toSet());

        if (session.getAnswers() == null)
            return null;

        List<String> answeredIds = new ArrayList<>(session.getAnswers().keySet());
        for (int i = answeredIds.size() - 1; i >= 0; i--) {
            if (existingIds.contains(answeredIds.get(i)))
                return answeredIds.get(i);
        }
        return null;
    }

    public boolean atomicRecover(UserSession session, Survey newDAG) {
        Set<String> existingIds = newDAG.getNodes().stream()
                .map(n -> n.getId())
                .collect(Collectors.toSet());

        if (session.getAnswers() == null)
            return true;

        return session.getAnswers().keySet().stream()
                .allMatch(existingIds::contains);
    }

    public boolean validatePath(Survey dag, Map<String, String> answers) {
        if (answers == null || answers.isEmpty())
            return false;

        List<String> terminalNodes = dag.getNodes().stream()
                .filter(node -> dag.getEdges().stream()
                        .noneMatch(e -> e.getFromNodeId().equals(node.getId())))
                .map(n -> n.getId())
                .collect(Collectors.toList());

        if (terminalNodes.isEmpty())
            return false;
        return terminalNodes.stream().allMatch(answers::containsKey);
    }
}