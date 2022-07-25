package map;

import java.util.*;

public class RandTest {

    public static void main(String[] args) {


        List<Stu> list = new ArrayList<>();

        list.add(new Stu("张三", 18));
        list.add(new Stu("张三", 20));
        list.add(new Stu("李四", 18));




    }


    static class Stu {

        String name;
        Integer age;

        public Stu(String name, Integer age) {
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

}
