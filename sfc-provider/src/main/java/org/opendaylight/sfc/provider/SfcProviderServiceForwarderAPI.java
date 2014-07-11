/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.sfc.provider;

import org.opendaylight.controller.sal.binding.api.data.DataModificationTransaction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.ServiceFunctionForwarders;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionary;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionaryBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionaryKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.service.function.path.SfpServiceFunction;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * This class has the APIs to operate on the ServiceFunction
 * datastore.
 *
 * It is normally called from onDataChanged() through a executor
 * service. We need to use an executor service because we can not
 * operate on a datastore while on onDataChanged() context.
 * @see org.opendaylight.sfc.provider.SfcProviderSfEntryDataListener
 *
 *
 * <p>
 * @author Reinaldo Penno (rapenno@gmail.com)
 * @version 0.1
 * @since       2014-06-30
 */
public class SfcProviderServiceForwarderAPI implements Runnable {
    private ServiceFunction serviceFunction;
    private static final Logger LOG = LoggerFactory.getLogger(SfcProviderSfEntryDataListener.class);
    private static final OpendaylightSfc odlSfc = OpendaylightSfc.getOpendaylightSfcObj();
    public enum OperationType {CREATE, DELETE}

    private OperationType operation = OperationType.CREATE;

    SfcProviderServiceForwarderAPI (ServiceFunction sf, OperationType type) {
        this.serviceFunction = sf;
        this.operation = type;
    }


    public static  SfcProviderServiceForwarderAPI getSfcProviderCreateServiceForwarderAPI(ServiceFunction sf) {
        return new SfcProviderServiceForwarderAPI(sf, OperationType.CREATE);
    }


    public static  SfcProviderServiceForwarderAPI getSfcProviderDeleteServiceForwarderAPI(ServiceFunction sf) {
        return new SfcProviderServiceForwarderAPI(sf, OperationType.DELETE);
    }

    @Override
    public void run() {
        switch (operation) {
            case CREATE:
                createServiceFunctionForwarder(serviceFunction);
                break;
            case DELETE:
                deleteServiceFunctionForwarder(serviceFunction);
                break;
        }
    }

    public void createServiceFunctionForwarder (ServiceFunction serviceFunction) {

        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);
        InstanceIdentifier<ServiceFunctionForwarder> sffIID;
        ServiceFunctionForwarderKey serviceFunctionForwarderKey =
                new ServiceFunctionForwarderKey(serviceFunction.getServiceFunctionForwarder());
        sffIID = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                .child(ServiceFunctionForwarder.class, serviceFunctionForwarderKey)
                .build();

        ServiceFunctionForwarderBuilder serviceFunctionForwarderBuilder = new ServiceFunctionForwarderBuilder();
        serviceFunctionForwarderBuilder.setName(serviceFunction.getServiceFunctionForwarder());

        ArrayList<ServiceFunctionDictionary> serviceFunctionDictionaryList = new ArrayList<>();
        ServiceFunctionDictionaryBuilder serviceFunctionDictionaryBuilder = new ServiceFunctionDictionaryBuilder();
        serviceFunctionDictionaryBuilder.setName(serviceFunction.getName()).setType(serviceFunction.getType())
                .setServiceFunctionForwarder(serviceFunction.getServiceFunctionForwarder())
                .setSfDataPlaneLocator(serviceFunction.getSfDataPlaneLocator());
        serviceFunctionDictionaryList.add(serviceFunctionDictionaryBuilder.build());

        serviceFunctionForwarderBuilder.setServiceFunctionDictionary(serviceFunctionDictionaryList);

        final DataModificationTransaction t = odlSfc.dataProvider
                .beginTransaction();
        t.putConfigurationData(sffIID, serviceFunctionForwarderBuilder.build());

        try {
            t.commit().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Failed to create Service Function Forwarder", e);
        }
        LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);

    }

    public void deleteServiceFunctionForwarder (ServiceFunction serviceFunction) {

        /*
         * TODO: We assume that if a ServiceFunction exists it belongs to a ServiceFunctionForwarder
         *
         * But this is not necessarily always true since the SFF could be deleted through
         * RESTconf. So, later more checks will be necessary.
         */


        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);
        InstanceIdentifier<ServiceFunctionDictionary> sffIID;
        ServiceFunctionForwarderKey serviceFunctionForwarderKey =
                new ServiceFunctionForwarderKey(serviceFunction.getServiceFunctionForwarder());
        ServiceFunctionDictionaryKey serviceFunctionDictionaryKey =
                new ServiceFunctionDictionaryKey(serviceFunction.getName());
        sffIID = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                .child(ServiceFunctionForwarder.class, serviceFunctionForwarderKey)
                .child(ServiceFunctionDictionary.class, serviceFunctionDictionaryKey )
                .build();
        final DataModificationTransaction t = odlSfc.dataProvider
                .beginTransaction();
        t.removeConfigurationData(sffIID);

        try {
            t.commit().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Failed to delete Service Function Forwarder", e);
        }
        LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);
    }

    public static ServiceFunctionForwarder readServiceFunctionForwarder(String name) {
        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);
        ServiceFunctionForwarderKey serviceFunctionForwarderKey =
                new ServiceFunctionForwarderKey(name);
        InstanceIdentifier<ServiceFunctionForwarder> sffIID;
        sffIID = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                .child(ServiceFunctionForwarder.class, serviceFunctionForwarderKey)
                .build();
        DataObject dataObject = odlSfc.dataProvider.readConfigurationData(sffIID);
        if (dataObject instanceof ServiceFunctionForwarder) {
            ServiceFunctionForwarder serviceFunctionForwarder = (ServiceFunctionForwarder) dataObject;
            return serviceFunctionForwarder;
        } else {
            return null;
        }
    }

    public static void addPathIdtoServiceFunctionForwarder(ServiceFunctionPath serviceFunctionPath) {
        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);


        List<SfpServiceFunction> sfpServiceFunctionArrayList = serviceFunctionPath.getSfpServiceFunction();
        for (SfpServiceFunction sfpServiceFunction : sfpServiceFunctionArrayList) {

            ServiceFunctionForwarderKey serviceFunctionForwarderKey =
                    new ServiceFunctionForwarderKey(sfpServiceFunction.getName());
            InstanceIdentifier<ServiceFunctionForwarder> sffIID;
            sffIID = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                    .child(ServiceFunctionForwarder.class, serviceFunctionForwarderKey)
                    .build();

            ServiceFunctionForwarder serviceFunctionForwarder =  readServiceFunctionForwarder (sfpServiceFunction.getServiceFunctionForwarder());
            if (serviceFunctionForwarder != null) {

                ServiceFunctionForwarderBuilder serviceFunctionForwarderBuilder = new ServiceFunctionForwarderBuilder();
                serviceFunctionForwarderBuilder.setPathId(serviceFunctionPath.getPathId());
                serviceFunctionForwarderBuilder.setName(sfpServiceFunction.getServiceFunctionForwarder());
                serviceFunctionForwarderBuilder.setSffDataPlaneLocator(serviceFunctionForwarder.getSffDataPlaneLocator());
                serviceFunctionForwarderBuilder.setServiceFunctionDictionary(serviceFunctionForwarder.getServiceFunctionDictionary());
                final DataModificationTransaction t = odlSfc.dataProvider
                        .beginTransaction();
                t.putConfigurationData(sffIID, serviceFunctionForwarderBuilder.build());
                try {
                    t.commit().get();
                } catch (ExecutionException | InterruptedException e) {
                    LOG.warn("Failed to create Service Function Forwarder", e);
                }
            }
        }

        LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);


    }
}
