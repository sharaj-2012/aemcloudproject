package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.config.LoginCredConfiguration;
import com.aemcloudproject.core.services.LoginCredConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.util.Arrays;

@Component(service = LoginCredConfigService.class)
@Designate(ocd = LoginCredConfiguration.class)
public class LoginCredConfigServiceImpl implements LoginCredConfigService {

    private String firstName;
    private int age;
    private boolean isAdult;
    private String[] hobbies;

    @Activate
    @Modified
    public void activate(LoginCredConfiguration loginCredConfiguration){
        this.firstName = loginCredConfiguration.firstName();
        this.age = loginCredConfiguration.age();
        this.isAdult = loginCredConfiguration.isAdult();
        this.hobbies = loginCredConfiguration.hobbies();
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public int getAge() {
        return age;
    }

    @Override
    public boolean isAdult() {
        return isAdult;
    }

    @Override
    public String[] getHobbies() {
        return hobbies;
    }

    @Override
    public String toString() {
        return "LoginCredConfigSericeImpl{" +
                "firstName='" + firstName + '\'' +
                ", age=" + age +
                ", isAdult=" + isAdult +
                ", hobbies=" + Arrays.toString(hobbies) +
                '}';
    }
}
