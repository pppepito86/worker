package org.pesho.judge;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
@TestConfiguration
public class ProblemsTest {

	@Autowired
	private WebApplicationContext context;
	
    @Value("${work.dir}")
    private String workDir;
	
	private MockMvc mvc;

	@Before
	public void setUp() {
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}
	
	@After
	public void clean() throws Exception {
		try {
			FileUtils.forceDelete(new File(workDir));
		} catch (Exception e) {
		}
	}

	@Test
	public void testCreateProblem() throws Exception {
		mvc.perform(get("/api/v1/problems")).andExpect(jsonPath("$", hasSize(0)));
		InputStream is = this.getClass().getClassLoader().getResourceAsStream("tests.zip");
		MockMultipartFile multipartFile = new MockMultipartFile("file", "tests.zip", "text/plain", is);
		this.mvc.perform(fileUpload("/api/v1/problems/1")
				.file(multipartFile))
				.andExpect(status().isCreated());
		
		mvc.perform(get("/api/v1/problems"))
				.andExpect(jsonPath("$", hasSize(1)));
	}
	
}
