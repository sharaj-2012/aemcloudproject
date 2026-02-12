package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.ContactUsService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ContactUsService.class,immediate = true)
public class ContactUsServiceImpl implements ContactUsService {

    private static final Logger log = LoggerFactory.getLogger(ContactUsServiceImpl.class);


}
