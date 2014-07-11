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
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChain;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.service.function.chain.SfcServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.ServiceFunctionPaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPathBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.service.function.path.SfpServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.service.function.path.SfpServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sft.rev140701.service.function.types.ServiceFunctionType;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sft.rev140701.service.function.types.service.function.type.SftServiceFunctionName;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * This class has the APIs to operate on the ServiceFunctionPath
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
public class SfcProviderServicePathAPI implements Runnable {

    private ServiceFunctionChain serviceFunctionChain;
    private static final Logger LOG = LoggerFactory.getLogger(SfcProviderSfEntryDataListener.class);
    private static final OpendaylightSfc odlSfc = OpendaylightSfc.getOpendaylightSfcObj();
    public enum OperationType {CREATE, DELETE}
    public static int numCreatedServicePath = 0;

    private OperationType operation = OperationType.CREATE;

    SfcProviderServicePathAPI (ServiceFunctionChain sfc, OperationType type) {
        this.serviceFunctionChain = sfc;
        this.operation = type;
    }


    public static  SfcProviderServicePathAPI getSfcProviderCreateServicePathAPI(ServiceFunctionChain sfc) {
        return new SfcProviderServicePathAPI(sfc, OperationType.CREATE);
    }


    public static  SfcProviderServicePathAPI getSfcProviderDeleteServicePathAPI(ServiceFunctionChain sfc) {
        return new SfcProviderServicePathAPI(sfc, OperationType.DELETE);
    }

    @Override
    public void run() {
        switch (operation) {
            case CREATE:
                createServiceFunctionPathEntry(serviceFunctionChain);
                break;
            case DELETE:
                deleteServiceFunctionPathEntry(serviceFunctionChain);
                break;
        }
    }

    public void createServiceFunctionPathEntry (ServiceFunctionChain serviceFunctionChain) {

        LOG.info("\n####### Start: {}", Thread.currentThread().getStackTrace()[1]);

        ServiceFunctionPathBuilder serviceFunctionPathBuilder = new ServiceFunctionPathBuilder();
        ArrayList<SfpServiceFunction> sfpServiceFunctionArrayList= new ArrayList<>();
        String serviceFunctionChainName = serviceFunctionChain.getName();
        SfpServiceFunctionBuilder sfpServiceFunctionBuilder = new SfpServiceFunctionBuilder();
        int pathId = numCreatedServicePath + 1;

        /*
         * For each ServiceFunction in the ServiceFunctionChain list we get its type use it to index
         * the list of service function by type.
         */
        List<SfcServiceFunction> SfcServiceFunctionList = serviceFunctionChain.getSfcServiceFunction();
        for (SfcServiceFunction sfcServiceFunction : SfcServiceFunctionList) {
            LOG.info("\n########## Updated ServiceFunction name: {}", sfcServiceFunction.getName());

            /*
             * We iterate thorough the list of service function types and for each one we get a suitable
             * Service Function
             */

            ServiceFunctionType serviceFunctionType = SfcProviderServiceTypeAPI.readServiceFunctionType(sfcServiceFunction.getType());
            if (serviceFunctionType != null) {
                for (SftServiceFunctionName sftServiceFunctionName : serviceFunctionType.getSftServiceFunctionName()) {
                    String serviceFunctionName  = sftServiceFunctionName.getName();
                    if (serviceFunctionName != null) {
                        ServiceFunction serviceFunction = SfcProviderServiceFunctionAPI
                                .readServiceFunction(serviceFunctionName);
                        if (serviceFunction != null) {
                            sfpServiceFunctionBuilder.setName(serviceFunctionName)
                                    .setServiceFunctionForwarder(serviceFunction.getServiceFunctionForwarder());

                        }
                        sfpServiceFunctionArrayList.add(sfpServiceFunctionBuilder.build());
                        break;
                    } else {
                        LOG.error("\n####### Could not find ServiceFunctionName in data store: {}",
                                Thread.currentThread().getStackTrace()[1]);
                        return;
                    }
                }
            } else {
                LOG.error("\n########## Could not find SFs of type: {}", Thread.currentThread().getStackTrace()[1]);
                return;
            }

        }

        //Build the service function path so it can be committed to datastore

        serviceFunctionPathBuilder.setSfpServiceFunction(sfpServiceFunctionArrayList);
        serviceFunctionPathBuilder.setName(serviceFunctionChainName + "-Path");
        // TODO: For now just monotonically incremented

        serviceFunctionPathBuilder.setPathId((long) pathId);
        // TODO: Find out the exact rules for service index generation
        serviceFunctionPathBuilder.setServiceIndex((short) (sfpServiceFunctionArrayList.size() + 1));

        ServiceFunctionPathKey serviceFuntionPathKey = new ServiceFunctionPathKey(serviceFunctionChainName + "-Path");
        InstanceIdentifier<ServiceFunctionPath> sfpIID;
        sfpIID = InstanceIdentifier.builder(ServiceFunctionPaths.class)
                .child(ServiceFunctionPath.class, serviceFuntionPathKey)
                .build();


        final DataModificationTransaction t = odlSfc.dataProvider
                .beginTransaction();
        t.putConfigurationData(sfpIID, serviceFunctionPathBuilder.build());

        try {
            t.commit().get();
            numCreatedServicePath++;
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Failed to create Service Path", e);
        }
        SfcProviderServiceForwarderAPI.addPathIdtoServiceFunctionForwarder(serviceFunctionPathBuilder.build());
        LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);

    }

    public void deleteServiceFunctionPathEntry (ServiceFunctionChain serviceFunctionChain) {

        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);
        String serviceFunctionChainName = serviceFunctionChain.getName();
        ServiceFunctionPathKey serviceFuntionPathKey = new ServiceFunctionPathKey(serviceFunctionChainName + "-Path");
        InstanceIdentifier<ServiceFunctionPath> sfpIID;
        sfpIID = InstanceIdentifier.builder(ServiceFunctionPaths.class)
                .child(ServiceFunctionPath.class, serviceFuntionPathKey)
                .build();

        final DataModificationTransaction t = odlSfc.dataProvider
                .beginTransaction();
        t.removeConfigurationData(sfpIID);
        try {
            t.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("deleteServiceFunctionPathEntry failed", e);
        }
        LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);

    }

    public static ServiceFunctionPath readServiceFunctionPath (String path) {
        LOG.info("\n########## Start: {}", Thread.currentThread().getStackTrace()[1]);
        ServiceFunctionPathKey serviceFuntionPathKey = new ServiceFunctionPathKey(path);
        InstanceIdentifier<ServiceFunctionPath> sfpIID;
        sfpIID = InstanceIdentifier.builder(ServiceFunctionPaths.class)
                .child(ServiceFunctionPath.class, serviceFuntionPathKey)
                .build();
        DataObject dataObject = odlSfc.dataProvider.readConfigurationData(sfpIID);
        if (dataObject instanceof ServiceFunctionPath) {
            ServiceFunctionPath serviceFunctionPath = (ServiceFunctionPath) dataObject;
            LOG.info("\n########## Stop: {}", Thread.currentThread().getStackTrace()[1]);
            return serviceFunctionPath;
        } else {
            return null;
        }
    }
}
