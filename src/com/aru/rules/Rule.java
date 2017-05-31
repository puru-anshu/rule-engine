
package com.aru.rules;


public class Rule implements Comparable<Rule> {

    private final String name;
    private final String expression;
    private final String outcome;
    private final int priority;
    private final String namespace;
    private final String description;

    /**
	 * @param name The name of the rule.  Should be unique within the namespace (tested when adding rules to the {@link Engine}).
	 * @param expression the rule expressed in expression language. all variables must come from the bean called "input".  The rule MUST evaluate to "true" if it is to be a candidate for execution.
	 * @param outcome The name of an action to run, if this rule is the winner.
	 * @param priority The priority, used in determining which rule to run, if many evaluate true.  The higher the value, the higher the priority.
	 * @param namespace A namespace, used for filtering rules.  The engine is passed a regular expression which is compared to this value.  Only matches are evaluated.
	 * @param description A description to help manage rules.
     */
    public Rule(final String name, final String expression, final String outcome, final int priority,
            final String namespace, final String description) {

        if(name == null) throw new AssertionError("name may not be null");
        if(expression == null) throw new AssertionError("expression may not be null");
        if(namespace == null) throw new AssertionError("namespace may not be null");
        
        this.name = name;
        this.expression = expression;
        this.outcome = outcome;
        this.priority = priority;
        this.namespace = namespace;
        this.description = description;
    }

    /**
     * See {@link #Rule(String, String, String, int, String, String)}, just without a description.
     */
    public Rule(final String name, final String expression, final String outcome, final int priority,
            final String namespace){
        this(name, expression, outcome, priority, namespace, null);
    }

    @Override
    public int compareTo(Rule r) {
        //reversed, since we want highest priority first in the list!
        return (this.priority < r.priority ? 1 : (this.priority == r.priority ? 0 : -1));
	}

	/**
	 * @return the {@link #namespace} concatenated with the {@link #name}, separated by a '.'
	 */
    public String getFullyQualifiedName(){
        return namespace + "." + name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((description == null) ? 0 : description.hashCode());
        result = prime * result
                + ((expression == null) ? 0 : expression.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
                + ((namespace == null) ? 0 : namespace.hashCode());
        result = prime * result + ((outcome == null) ? 0 : outcome.hashCode());
        result = prime * result + priority;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Rule other = (Rule) obj;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (expression == null) {
            if (other.expression != null)
                return false;
        } else if (!expression.equals(other.expression))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (namespace == null) {
            if (other.namespace != null)
                return false;
        } else if (!namespace.equals(other.namespace))
            return false;
        if (outcome == null) {
            if (other.outcome != null)
                return false;
        } else if (!outcome.equals(other.outcome))
            return false;
        if (priority != other.priority)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Rule [name=" + name + ", expression=" + expression
                + ", outcome=" + outcome + ", priority=" + priority
                + ", namespace=" + namespace + ", description=" + description
                + "]";
    }

    public String getName() {
        return name;
    }

    public String getExpression() {
        return expression;
    }

    public String getOutcome() {
        return outcome;
    }

    public int getPriority() {
        return priority;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDescription() {
        return description;
    }
    
}
