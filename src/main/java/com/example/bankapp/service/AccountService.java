package com.example.bankapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bankapp.model.Account;
import com.example.bankapp.model.Transaction;
import com.example.bankapp.repository.AccountRepository;
import com.example.bankapp.repository.TransactionRepository;

@Service
public class AccountService implements UserDetailsService {

	private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AccountRepository accountRepository;

	@Autowired
	TransactionRepository transactionRepository;

	public Account findAccountByUsername(String username) {
		return accountRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("Account not found!"));
	}

	public Account registerAccount(String username, String password) {
		if (accountRepository.findByUsername(username).isPresent()) {
			throw new RuntimeException("Username already exists.");
		}

		Account account = new Account();
		account.setUsername(username);
		account.setPassword(passwordEncoder.encode(password));
		account.setBalance(BigDecimal.ZERO);
		return accountRepository.save(account);
	}

	@Transactional
	public void deposit(Account account, BigDecimal amount) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Deposit amount must be greater than zero");
		}

		account.setBalance(account.getBalance().add(amount));
		accountRepository.save(account);
		Transaction transaction = new Transaction(amount, "Deposit", LocalDateTime.now(), account);
		transactionRepository.save(transaction);
	}

	@Transactional
	public void withdraw(Account account, BigDecimal amount) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Withdrawal amount must be greater than zero");
		}
		logger.info("Current balance: {}", account.getBalance());
		if (account.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Insufficient funds");
		}
		account.setBalance(account.getBalance().subtract(amount));
		accountRepository.save(account);
		logger.info("New balance after withdrawal: {}", account.getBalance());
		Transaction transaction = new Transaction(amount, "Withdrawal", LocalDateTime.now(), account);
		transactionRepository.save(transaction);
	}

	public List<Transaction> getTransactionHistory(Account account) {
		return transactionRepository.findByAccountId(account.getId());
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Account account = findAccountByUsername(username);
		if (account == null) {
			throw new UsernameNotFoundException("Username or Password not found");
		}
		return new Account(account.getUsername(), account.getPassword(), account.getBalance(),
				account.getTransactions(), authorities());
	}

	public Collection<? extends GrantedAuthority> authorities() {
		return Arrays.asList(new SimpleGrantedAuthority("User"));
	}

	@Transactional
	public void transferAmount(Account fromAccount, String toUsername, BigDecimal amount) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Transfer amount must be greater than zero");
		}

		if (fromAccount.getBalance().compareTo(amount) < 0) {
			throw new RuntimeException("Insufficient funds");
		}

		Account toAccount = accountRepository.findByUsername(toUsername)
				.orElseThrow(() -> new RuntimeException("Recipient account not found"));

		// Deduct
		fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
		accountRepository.save(fromAccount);

		// Add
		toAccount.setBalance(toAccount.getBalance().add(amount));
		accountRepository.save(toAccount);

		Transaction debitTransaction = new Transaction(amount, "Transfer Out to " + toAccount.getUsername(),
				LocalDateTime.now(), fromAccount);
		transactionRepository.save(debitTransaction);

		Transaction creditTransaction = new Transaction(amount, "Transfer In from " + fromAccount.getUsername(),
				LocalDateTime.now(), toAccount);
		transactionRepository.save(creditTransaction);
	}
	
	/*
	 * public Page<Transaction> getTransactions(int page, int size) { return
	 * transactionRepository.findAll(PageRequest.of(page, size)); }
	 */
}
