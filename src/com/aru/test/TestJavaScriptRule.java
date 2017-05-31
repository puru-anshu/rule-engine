package com.aru.test;


import com.aru.rules.AbstractAction;
import com.aru.rules.Engine;
import com.aru.rules.JavascriptEngine;
import com.aru.rules.Rule;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by anshuman on 23/06/16.
 */
public class TestJavaScriptRule {
    public static void main(String[] args) {
        Rule r1 = new Rule("SendEmailToUser", "input.config.sendUserEmail == true", "SendEmailToUser", 1, "com.aru.test.config");
        Rule r2 = new Rule("SendEmailToModerator", "input.config.sendAdministratorEmail == true && input.user.numberOfPostings < 5", "SendEmailToModerator", 2, "com.aru.test.config");
        List<Rule> rules = Arrays.asList(r1, r2);

        final List<String> log = new ArrayList<String>();

        AbstractAction<ForumSetup, Void> a1 = new AbstractAction<ForumSetup, Void>("SendEmailToUser") {
            @Override
            public Void execute(ForumSetup input) {
                log.add("Sending email to user!");
                return null;
            }
        };
        AbstractAction<ForumSetup, Void> a2 = new AbstractAction<ForumSetup, Void>("SendEmailToModerator") {
            @Override
            public Void execute(ForumSetup input) {
                log.add("Sending email to moderator!");
                return null;
            }
        };

        try {
            Engine engine = new Engine(rules, true);

            ForumSetup setup = new ForumSetup();
            setup.getConfig().setSendUserEmail(true);
            setup.getConfig().setSendAdministratorEmail(true);
            setup.getUser().setNumberOfPostings(6);

            List<Rule> matchingRules = engine.getMatchingRules("com.aru.test.config", setup);
            matchingRules.get(0).
            engine.executeAllActions("com.aru.test.config", setup, Arrays.asList(a1, a2));

//            System.out.println("matchingRules = " + matchingRules);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static final class Person {
        private String name;
        private Integer age;

        public Person(int age) {
            this.age = age;
        }

        public Person(String name) {
            this.name = name;
        }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    public static final class MyInput {
        private Person p1;
        private Person p2;

        public Person getP1() {
            return p1;
        }

        public void setP1(Person p1) {
            this.p1 = p1;
        }

        public Person getP2() {
            return p2;
        }

        public void setP2(Person p2) {
            this.p2 = p2;
        }
    }

    public static final class Account {
        private int ageInMonths;

        public void setAgeInMonths(int ageInMonths) {
            this.ageInMonths = ageInMonths;
        }

        public int getAgeInMonths() {
            return ageInMonths;
        }
    }

    public static final class TarifRequest {
        private Person p;
        private Account a;

        public Person getPerson() {
            return p;
        }

        public void setPerson(Person p) {
            this.p = p;
        }

        public Account getAccount() {
            return a;
        }

        public void setAccount(Account a) {
            this.a = a;
        }
    }

    public static final class TravelRequest {
        private int distance;
        private Map<Object, Object> map = new HashMap<Object, Object>();

        public TravelRequest(int distance) {
            this.distance = distance;
        }

        public void put(Object key, Object value) {
            map.put(key, value);
        }

        public int getDistance() {
            return distance;
        }

        @SuppressWarnings("rawtypes")
        public Map getMap() {
            return map;
        }
    }

    public static final class ForumSetup {
        private Config config = new Config();
        private User user = new User();

        public Config getConfig() {
            return config;
        }

        public User getUser() {
            return user;
        }
    }

    public static final class Config {
        private boolean sendUserEmail;
        private boolean sendAdministratorEmail;

        public void setSendUserEmail(boolean sendUserEmail) {
            this.sendUserEmail = sendUserEmail;
        }

        public void setSendAdministratorEmail(boolean sendAdministratorEmail) {
            this.sendAdministratorEmail = sendAdministratorEmail;
        }

        public boolean isSendAdministratorEmail() {
            return sendAdministratorEmail;
        }

        public boolean isSendUserEmail() {
            return sendUserEmail;
        }
    }

    public static final class User {
        private int numberOfPostings = 0;

        public void setNumberOfPostings(int numberOfPostings) {
            this.numberOfPostings = numberOfPostings;
        }

        public int getNumberOfPostings() {
            return numberOfPostings;
        }
    }

    public static final class Classroom {
        List<Person> students = new ArrayList<Person>();

        public List<Person> getStudents() {
            return students;
        }
    }
}
