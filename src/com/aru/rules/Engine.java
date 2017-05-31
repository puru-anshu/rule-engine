package com.aru.rules;



import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.util.*;


public class Engine {


    public static final String DEFAULT_INPUT_NAME = "input";
    protected final Set<String> uniqueOutcomes = new HashSet<String>();
    protected List<Rule> parsedRules;

    protected final boolean throwExceptionIfCompilationFails;
    protected final String inputName;

    public Engine(final Collection<Rule> rules, boolean throwExceptionIfCompilationFails) throws RuleCompileException, ParseException, DuplicateRuleNameException {
        this(rules, DEFAULT_INPUT_NAME, throwExceptionIfCompilationFails);

    }


    public Engine(final Collection<Rule> rules, String inputName, boolean throwExceptionIfCompilationFails) throws DuplicateRuleNameException, RuleCompileException, ParseException {
        this.inputName = inputName;
        parsedRules = new ArrayList<Rule>();
        parsedRules.addAll(rules);
        this.throwExceptionIfCompilationFails = throwExceptionIfCompilationFails;

    }


    /**
     * See {@link #getBestOutcome(String, Object)}, except that all namespaces will be considered.
     *
     * @param <Input> An input object to match against rules.
     */
    public <Input> String getBestOutcome(Input input) throws NoMatchingRuleFoundException {
        return getBestOutcome(null, input);
    }

    /**
     * Evaluates all rules against the input and returns the result of the outcome associated with the rule having the highest priority.
     *
     * @param <Input>          An input object to match against rules.
     * @param nameSpacePattern optional.  if not null, then only rules with matching namespaces are evaluated.
     * @param input            the Object containing all inputs to the expression language rule.
     * @return The outcome belonging to the best rule which is found.
     * @throws NoMatchingRuleFoundException If no matching rule was found.  Rules must evaluate to true in order to be candidates.
     */
    public <Input> String getBestOutcome(String nameSpacePattern, Input input) throws NoMatchingRuleFoundException {

        List<Rule> matches = getMatchingRules(nameSpacePattern, input);
        if (matches == null || matches.isEmpty()) {
            throw new NoMatchingRuleFoundException();
        } else {
            return matches.get(0).getOutcome();
        }
    }

    /**
     * See {@link #executeBestAction(String, Object, Collection)}, except that all namespaces will be considered.
     */
    public <Input, Output> Output executeBestAction(Input input, Collection<? extends IAction<Input, Output>> actions) throws NoMatchingRuleFoundException, NoActionFoundException, DuplicateRuleNameException {
        return executeBestAction(null, input, actions);
    }

    /**
     * Evaluates all rules against the input and returns the result of the action associated with the rule having the highest priority.
     *
     * @param nameSpacePattern optional.  if not null, then only rules with matching namespaces are evaluated.
     * @param input            the Object containing all inputs to the expression language rule.
     * @param actions          a collection of actions containing one action per possible outcome.  The action whose name is equal to the winning outcome will be executed.
     * @return The result of the {@link IAction} with the same name as the winning rules outcome.
     * @throws NoMatchingRuleFoundException If no matching rule was found.  Rules must evaluate to true in order to be candidates.
     * @throws NoActionFoundException       If no action with a name matching the winning rules outcome was found.
     * @throws DuplicateRuleNameException     if any actions have the same name.
     */
    public <Input, Output> Output executeBestAction(String nameSpacePattern, Input input, Collection<? extends IAction<Input, Output>> actions) throws NoMatchingRuleFoundException, NoActionFoundException, DuplicateRuleNameException {

        Map<String, IAction<Input, Output>> actionsMap = validateActions(actions);
        return actionsMap.get(getBestOutcome(nameSpacePattern, input)).execute(input);
    }

    /**
     * See {@link #executeAllActions(String, Object, Collection)}, except that all namespaces will be considered.
     * <b>NOTE THAT THIS METHOD DISREGARDS ANY RETURN VALUES OF ACTIONS!!</b>
     */
    public <Input, Output> void executeAllActions(Input input, Collection<? extends IAction<Input, Output>> actions) throws NoMatchingRuleFoundException, NoActionFoundException, DuplicateRuleNameException {
        executeAllActions(null, input, actions);
    }

    /**
     * Evaluates all rules against the input and then executes all action associated with the positive rules outcomes, in order of highest priority first.<br>
     * <br>
     * Any outcome is only ever executed once!<br>
     * <br>
     * <b>NOTE THAT THIS METHOD DISREGARDS ANY RETURN VALUES OF ACTIONS!!</b>
     *
     * @param <Input>          The type of input to the actions.
     * @param <Output>         The type of output from the actions.
     * @param nameSpacePattern optional.  if not null, then only rules with matching namespaces are evaluated.
     * @param input            the Object containing all inputs to the expression language rule.
     * @param actions          a collection of actions containing one action per possible outcome.  The actions whose names is equal to the positive outcomes will be executed.
     * @throws NoMatchingRuleFoundException If no matching rule was found.  Rules must evaluate to true in order to be candidates.
     * @throws NoActionFoundException       If no action with a name matching the winning rules outcome was found.
     * @throws DuplicateRuleNameException     if any actions have the same name.
     */
    public <Input, Output> void executeAllActions(String nameSpacePattern, Input input, Collection<? extends IAction<Input, Output>> actions) throws NoMatchingRuleFoundException, NoActionFoundException, DuplicateRuleNameException {

        Map<String, IAction<Input, Output>> actionsMap = validateActions(actions);

        List<Rule> matchingRules = getMatchingRules(nameSpacePattern, input);

        Set<String> executedOutcomes = new HashSet<String>();
        for (Rule r : matchingRules) {
            //only run, if not already run!
            if (!executedOutcomes.contains(r.getOutcome())) {

                actionsMap.get(r.getOutcome()).execute(input);

                executedOutcomes.add(r.getOutcome());
            }
        }


    }

    private <Input, Output> Map<String, IAction<Input, Output>> validateActions(Collection<? extends IAction<Input, Output>> actions) throws DuplicateRuleNameException, NoActionFoundException {
        //do any actions have duplicate names?
        Map<String, IAction<Input, Output>> actionsMap = new HashMap<String, IAction<Input, Output>>();
        for (IAction<Input, Output> a : actions) {
            if (actionsMap.containsKey(a.getName())) {
                throw new DuplicateRuleNameException("The name " + a.getName() + " was found in a different action.  Action names must be unique.");
            } else {
                actionsMap.put(a.getName(), a);
            }
        }

        //do we have at least one action for every possible outcome?
        //better to test now, rather than in production...
        //n.b. subrules have outcome == null, so skip them
        for (String outcome : uniqueOutcomes) {
            if (outcome != null && !actionsMap.containsKey(outcome)) {
                throw new NoActionFoundException("No action has been associated with the outcome \"" + outcome + "\"");
            }
        }

        return actionsMap;
    }

    /**
     * See {@link #getMatchingRules(String, Object)}, except that all namespaces will be considered.
     */
    public <Input> List<Rule> getMatchingRules(Input input) {
        return getMatchingRules(null, input);
    }


    public <Input> List<Rule> getMatchingRules(String nameSpacePattern, Input input) {

        List<Rule> matchingRules = new ArrayList<Rule>();

        Context rhino = Context.enter();
        // Turn off optimization to make Rhino Android compatible
        rhino.setOptimizationLevel(-1);
        try {
            Scriptable scope = rhino.initStandardObjects();
            String source = new Scanner(Engine.class.getResourceAsStream("js/lodash.min.js"), "UTF-8").useDelimiter("\\A").next();
            rhino.evaluateString(scope, source, "JavaScript", 1, null);
            rhino.evaluateString(scope, getJsonString(input), inputName, 1, null);
            for (Rule r : parsedRules) {
                Object result = rhino.evaluateString(scope, r.getExpression(), r.getName(), 1, null);
                if (String.valueOf(result).equals("true")) {
                    matchingRules.add(r);
                }
            }
            //order by priority!
            Collections.sort(matchingRules);
            return matchingRules;
        } catch (Exception e) {
            if (throwExceptionIfCompilationFails) {
                throw new IllegalArgumentException("Failed to run script ", e);
            } else {
                //e.printStackTrace();
            }
        } finally {
            Context.exit();
        }
        return matchingRules;
    }

    private <Input> String getJsonString(Input input) {
        return " var " + inputName + " = " + new Gson().toJson(input) + " ;";
    }


}