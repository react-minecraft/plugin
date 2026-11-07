package com.g4vrk.react.config.model;

import java.util.List;

/**
 * What every check config has.
 *
 * <p>{@code debug} is the action list that shows a check's debug lines to the viewers of
 * {@code /react debug}.</p>
 */
public interface CheckConfig {

    List<String> debug();

}
