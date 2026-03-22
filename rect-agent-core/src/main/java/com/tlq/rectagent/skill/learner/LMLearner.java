package com.tlq.rectagent.skill.learner;

import java.util.List;

public interface LMLearner {
    String learn(List<String> documentContents);
}