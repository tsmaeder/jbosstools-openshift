/*******************************************************************************
 * Copyright (c) 2015-2016 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.openshift.test.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.jboss.tools.openshift.common.core.ICredentialsPrompter;
import org.jboss.tools.openshift.core.connection.Connection;
import org.jboss.tools.openshift.internal.common.core.security.SecureStore;
import org.jboss.tools.openshift.internal.common.core.security.SecureStoreException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.IAuthorizationContext;
import com.openshift.restclient.authorization.IAuthorizationStrategy;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IClientCapability;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.user.IUser;

/**
 * @author Jeff Cantrill
 * @author Andre Dietisheim
 */
@RunWith(MockitoJUnitRunner.class)
public class ConnectionTest {
	
	private Connection connection;
	@Mock private SecureStore store;
	@Mock private IClient client;
	private TestableConnection testableConnection;
	
	@Before
	public void setup() throws Exception {
		when(client.getBaseURL()).thenReturn(new URL("https://localhost:8443"));
		doReturn(mock(IAuthorizationStrategy.class)).when(client).getAuthorizationStrategy();		
		doReturn(mockAuthorizationContext("foobar", "42", true)).when(client).getContext(anyString());
			
		this.connection = new Connection(client, null, null);
		
		doReturn("42").when(store).get(anyString()); // password/token

		this.testableConnection = spy(new TestableConnection(client, null, null, store));
	}

	private IAuthorizationContext mockAuthorizationContext(String username, String token, boolean isAuthorized) {
		IAuthorizationContext authorizationContext = mock(IAuthorizationContext.class);
		doReturn(isAuthorized).when(authorizationContext).isAuthorized();
		doReturn(token).when(authorizationContext).getToken();

		IUser user = mock(IUser.class);
		doReturn(username).when(user).getName();
		doReturn(user).when(authorizationContext).getUser();

		return authorizationContext;
	}

	@Test
	public void ownsResource_should_return_true_if_clients_are_same() {
		Map<String, Object> mocks = givenAResourceThatAcceptsAVisitorForIClientCapability(client);
		assertTrue(connection.ownsResource((IResource)mocks.get("resource")));
	}

	@Test
	public void ownsResource_should_return_false_when_clients_are_different() {
		IClient diffClient = mock(IClient.class);
		Map<String, Object> mocks = givenAResourceThatAcceptsAVisitorForIClientCapability(diffClient);
		assertFalse(connection.ownsResource((IResource)mocks.get("resource")));
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, Object> givenAResourceThatAcceptsAVisitorForIClientCapability(IClient client){
		final IClientCapability capability = mock(IClientCapability.class);
		when(capability.getClient()).thenReturn(client);
		IResource resource = mock(IResource.class);
		when(resource.accept(any(CapabilityVisitor.class), any())).thenAnswer(new Answer<IClient>() {
			@Override
			public IClient answer(InvocationOnMock invocation) throws Throwable {
				CapabilityVisitor<IClientCapability, IClient> visitor = (CapabilityVisitor<IClientCapability, IClient>)invocation.getArguments()[0];
				return visitor.visit(capability);
			}
		});
		Map<String, Object> mocks = new HashMap<>();
		mocks.put("capability", capability);
		mocks.put("resource", resource);
		return mocks;
	}
	
	@Test
	public void getResource_with_kind_should_call_client() {
		// given
		// when
		connection.getResources(ResourceKind.PROJECT);
		// then
		verify(client).list(eq(ResourceKind.PROJECT), anyString());
	}

	@Test
	public void getHost_should_return_host() {
		assertEquals("https://localhost:8443", connection.getHost());
	}

	@Test
	public void getScheme_should_return_scheme() {
		// given
		// when
		// then
		assertEquals("https://", connection.getScheme());
	}
	
	@Test
	public void should_not_equals_if_different_user() throws Exception {
		// given
		Connection two = new Connection("https://localhost:8443", null, null);
		two.setUsername("foo");
		// when
		// then
		assertThat(connection).isNotEqualTo(two);
	}

	@Test
	public void should_not_equals_if_different_scheme() throws Exception {
		// given
		Connection two = new Connection("http://localhost:8443", null, null);
		// when
		// then
		assertThat(connection).isNotEqualTo(two);
	}

	@Test
	public void should_not_equals_if_different_host() throws Exception {
		// given
		Connection two = new Connection("https://openshift.redhat.com:8443", null, null);
		two.setUsername("foo");
		// when
		// then
		assertThat(connection).isNotEqualTo(two);
	}

	@Test
	public void should_equals_if_same_url_and_user() throws Exception {
		// given
		connection.setUsername("foo");
		Connection two = new Connection("https://localhost:8443", null, null);
		two.setUsername("foo");
		// when
		// then
		assertThat(connection).isEqualTo(two);
	}

	@Test
	public void should_equal_if_different_password() throws Exception {
		// given
		connection.setUsername("bar");
		connection.setPassword("42");

		Connection two = new Connection("https://localhost:8443", null, null);
		two.setUsername("bar");
		two.setPassword("42");
		// when
		// then
		assertThat(connection).isEqualTo(two);
	}
	
	@Test
	public void should_equal_if_different_token() throws Exception {
		// given
		connection.setUsername("kung");
		connection.setToken("foo");

		Connection two = new Connection("https://localhost:8443", null, null);
		two.setUsername("kung");
		two.setToken("foo");
		// when
		// then
		assertThat(connection).isEqualTo(two);
	}

	@Test
	public void should_not_credentials_equal_if_different_username() throws Exception {
		// given
		Connection two = (Connection)connection.clone();
		two.setUsername("userTwo");
		// when
		// then
		assertNotEquals(connection, two);
		assertFalse(two.credentialsEqual(connection));
	}

	@Test
	public void should_not_credentialsEqual_if_different_password() throws Exception {
		// given
		Connection two = (Connection) connection.clone();
		two.setPassword("aPassword123");
		// when
		// then
		assertEquals(connection, two);
		assertFalse(two.credentialsEqual(connection));
	}

	@Test
	public void should_not_credentialsEqual_if_different_token() throws Exception {
		// given
		Connection two = (Connection) connection.clone();
		two.setToken("tokenTwo");
		// when
		// then
		assertEquals(connection, two);
		assertFalse(two.credentialsEqual(connection));
	}

	@Test
	public void shouldCredentialsEqualForClone() throws Exception {
		// given
		Connection two = (Connection)connection.clone();
		// when
		// then
		assertEquals(connection, two);
		assertTrue(two.credentialsEqual(connection));
	}

	@Test
	public void should_not_overwrite_client_authorization_strategy_on_isConnected() {
		// given
		// when
		connection.isConnected(new NullProgressMonitor());
		// then
		verify(client, never()).setAuthorizationStrategy(any(IAuthorizationStrategy.class));
	}

	@Test
	public void should_set_client_authorization_strategy_when_not_present_on_isConnected() {
		// given
		doReturn(null).when(client).getAuthorizationStrategy();
		doReturn(mockAuthorizationContext(null, null, false)).when(client).getContext(anyString());
		// when
		connection.isConnected(new NullProgressMonitor());
		// then
		verify(client).setAuthorizationStrategy(any(IAuthorizationStrategy.class));
	}

	@Test
	public void should_not_connect_upon_isConnected_if_is_not_authorized() {
		// given
		doReturn(mockAuthorizationContext(null, null, false)).when(client).getContext(anyString());
		// when
		testableConnection.isConnected(new NullProgressMonitor());
		// then
		verify(testableConnection, never()).connect();
	}

	@Test
	public void clone_should_create_identical_connection() throws Exception {
		// pre-conditions
		Connection connection = new Connection("https://localhost:8443", null, null);
		connection.setUsername("shadowman");
		connection.setPassword("foo");
		connection.setRememberPassword(false);
		connection.setToken("1234567890abcdefghijklmnopqrstuvz%$");
		connection.setRememberToken(true);

		// operations
		Connection clonedConnection = (Connection) connection.clone();

		// verifications
		assertThat(clonedConnection.getUsername()).isEqualTo(clonedConnection.getUsername());
		assertThat(clonedConnection.getPassword()).isEqualTo(clonedConnection.getPassword());
		assertThat(clonedConnection.isRememberPassword()).isEqualTo(clonedConnection.isRememberPassword());
		assertThat(clonedConnection.getToken()).isEqualTo(connection.getToken());
		assertThat(clonedConnection.isRememberToken()).isEqualTo(clonedConnection.isRememberToken());
		assertThat(clonedConnection.getHost()).isEqualTo(clonedConnection.getHost());
	}

	@Test
	public void should_update_connection_by_connection() throws Exception {
		// pre-conditions
		Connection connection = new Connection("http://localhost:8443", null, null);
		connection.setUsername("foo");
		connection.setPassword("bar");
		connection.setToken("000000000000111111111");
		connection.setRememberPassword(true);
		connection.setRememberToken(false);

		String newUsername = "shadowman";
		String newHost = "http://www.redhat.com";
		String newPassword = "1q2w3e";
		String newToken = "1234567890abcdefghijklmz";
		boolean newRememberPassword = false;
		boolean newRememberToken = true;
		Connection updatingConnection = new Connection(newHost, null, null);
		updatingConnection.setUsername(newUsername);
		updatingConnection.setPassword(newPassword);
		updatingConnection.setRememberPassword(newRememberPassword);
		updatingConnection.setToken(newToken);
		updatingConnection.setRememberToken(newRememberToken);
		
		// operations
		connection.update(updatingConnection);

		// verifications
		assertThat(connection.getHost()).isEqualTo(newHost);
		assertThat(connection.getUsername()).isEqualTo(newUsername);
		assertThat(connection.getPassword()).isEqualTo(newPassword);
		assertThat(connection.getToken()).isEqualTo(newToken);
		assertThat(connection.isRememberPassword()).isEqualTo(newRememberPassword);
		assertThat(connection.isRememberToken()).isEqualTo(newRememberToken);
	}

	@Test 
	public void should_add_extendedProperty() throws Exception {
		// given
		Connection connection = new Connection("http://localhost", null, null);
		assertThat(connection.getExtendedProperties()).isEmpty();
		// when
		connection.setExtendedProperty("foo", "bar");
		// then
		assertThat(connection.getExtendedProperties()).hasSize(1);
		assertThat(connection.getExtendedProperties()).containsEntry("foo", "bar");
	}

	@Test 
	public void shouldReplaceExtendedProperty() throws Exception {
		// given
		Connection connection = new Connection("http://localhost", null, null);
		connection.setExtendedProperty("kung", "foo");
		connection.setExtendedProperty("foo", "bar");
		assertThat(connection.getExtendedProperties())
			.containsEntry("kung", "foo").containsEntry("foo", "bar");
		// when
		connection.setExtendedProperties(new HashMap<String, Object>() {{ put("foo", "bar"); }});
		// then
		assertThat(connection.getExtendedProperties()).hasSize(1);
		assertThat(connection.getExtendedProperties()).containsExactly(MapEntry.entry("foo", "bar"));
	}

	@Test
	public void should_load_password_if_not_loaded_yet() throws Exception {
		// given
		// when
		testableConnection.getPassword();
		// then
		verify(store).get(anyString());
	}
	
	@Test
	public void should_not_load_password_if_already_loaded() throws Exception {
		// given
		testableConnection.getPassword();
		// when
		testableConnection.getPassword();
		// then
		verify(store, times(1)).get(anyString()); // only loaded 1x
	}

	@Test
	public void should_not_load_password_if_already_set() throws Exception {
		// given
		testableConnection.setPassword("42");
		// when
		testableConnection.getPassword();
		// then
		verify(store, never()).get(anyString());
	}

	@Test
	public void should_setRememberPassword_if_loading_password() throws SecureStoreException {
		// given
		// when
		testableConnection.getPassword();
		// given
		verify(store).get(anyString()); // load password
		// then
		verify(testableConnection).setRememberPassword(true);
	}

	@Test
	public void should_save_password_when_connecting_successfully() throws Exception {
		// given
		testableConnection.setAuthScheme(IAuthorizationContext.AUTHSCHEME_BASIC);
		testableConnection.setRememberPassword(true);
		testableConnection.setPassword("kungfoo");
		// when
		testableConnection.connect();
		// then
		verify(store, atLeast(1)).put(anyString(), eq("kungfoo"));
	}

	public class TestableConnection extends Connection {

		private SecureStore store;

		public TestableConnection(IClient client, ICredentialsPrompter credentialsPrompter,
				ISSLCertificateCallback sslCertCallback, SecureStore store) {
			super(client, credentialsPrompter, sslCertCallback);
			this.store = store;
		}

		@Override
		protected SecureStore getSecureStore() {
			return store;
		}

		@Override
		protected void saveAuthSchemePreference() {
			super.saveAuthSchemePreference();
		}
		
	}

}
