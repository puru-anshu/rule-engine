
package com.aru.rules;


public class SubRule extends Rule {

	/**
	 * @param name The name of the rule.  Should be unique within the namespace (checked when adding rules to the engine).
	 * @param expression the rule, expressed in expression language. all variables must come from the bean called "input".  The rule MUST evaluate to "true" if it is to be a candidate for execution.
	 * @param namespace For a sub rule to be used by a rule, it must have the same namespace transactionCompleted the rule.
	 * @param description A description to help manage rules.
	 */
    public SubRule(String name, String expression, String namespace, String description) {
        super(name, expression, null, -1, namespace, description);
    }

    /**
     * See {@link #SubRule(String, String, String, String)}, just without a description.
     */
    public SubRule(String name, String expression, String namespace){
        this(name, expression, namespace, null);
    }
    
}
