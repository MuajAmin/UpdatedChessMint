/* === JAVASCRIPT FOR UPDATEDCHESSMINT WEBSITE === */

'use strict';

// ============================
// NAVBAR SCROLL BEHAVIOR
// ============================
const navbar = document.getElementById('navbar');

function handleNavbarScroll() {
    if (window.scrollY > 60) {
        navbar.classList.add('scrolled');
    } else {
        navbar.classList.remove('scrolled');
    }
}

window.addEventListener('scroll', handleNavbarScroll, { passive: true });
handleNavbarScroll();

// ============================
// MOBILE MENU
// ============================
const hamburger = document.getElementById('nav-hamburger');
const navLinks = document.getElementById('nav-links');
const navCta = document.querySelector('.nav-cta');

let menuOpen = false;

if (hamburger) {
    hamburger.addEventListener('click', function () {
        menuOpen = !menuOpen;

        if (menuOpen) {
            navLinks.style.display = 'flex';
            navLinks.style.flexDirection = 'column';
            navLinks.style.position = 'fixed';
            navLinks.style.top = '64px';
            navLinks.style.left = '0';
            navLinks.style.right = '0';
            navLinks.style.background = 'rgba(8, 10, 15, 0.97)';
            navLinks.style.backdropFilter = 'blur(20px)';
            navLinks.style.padding = '20px 24px';
            navLinks.style.borderBottom = '1px solid rgba(255,255,255,0.08)';
            navLinks.style.zIndex = '999';
            navLinks.style.gap = '4px';
            navLinks.style.animation = 'fadeDown 0.2s ease forwards';
        } else {
            navLinks.style.display = 'none';
        }

        // Animate hamburger
        const spans = hamburger.querySelectorAll('span');
        if (menuOpen) {
            spans[0].style.transform = 'translateY(7px) rotate(45deg)';
            spans[1].style.opacity = '0';
            spans[2].style.transform = 'translateY(-7px) rotate(-45deg)';
        } else {
            spans[0].style.transform = '';
            spans[1].style.opacity = '';
            spans[2].style.transform = '';
        }
    });
}

// Close menu on nav link click
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', () => {
        if (menuOpen) {
            hamburger.click();
        }
    });
});

// ============================
// SMOOTH SCROLL
// ============================
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        const href = this.getAttribute('href');
        if (href === '#') return;

        const target = document.querySelector(href);
        if (target) {
            e.preventDefault();
            const offset = 80;
            const targetTop = target.getBoundingClientRect().top + window.scrollY - offset;
            window.scrollTo({ top: targetTop, behavior: 'smooth' });
        }
    });
});

// ============================
// SCROLL REVEAL ANIMATIONS
// ============================
function initScrollReveal() {
    // Add reveal class to elements
    const revealSelectors = [
        '.feature-card',
        '.step',
        '.credit-card',
        '.engine-content',
        '.engine-terminal',
        '.section-header',
    ];

    revealSelectors.forEach(sel => {
        document.querySelectorAll(sel).forEach((el, i) => {
            el.classList.add('reveal');
            el.style.transitionDelay = `${i * 60}ms`;
        });
    });

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, {
        rootMargin: '0px 0px -60px 0px',
        threshold: 0.1
    });

    document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
}

// ============================
// ENGINE OPTION TOGGLE
// ============================
function initEngineOptions() {
    const options = document.querySelectorAll('.engine-option');
    options.forEach(opt => {
        opt.addEventListener('click', function () {
            options.forEach(o => o.classList.remove('active'));
            this.classList.add('active');
        });
    });
}

// ============================
// DEPTH BAR ANIMATION (MOCKUP)
// ============================
function animateMockup() {
    const evalFill = document.querySelector('.eval-fill');
    if (!evalFill) return;

    const evalValues = [38, 55, 45, 62, 50, 30, 65];
    let idx = 0;

    setInterval(() => {
        idx = (idx + 1) % evalValues.length;
        evalFill.style.transition = 'height 1s ease';
        evalFill.style.height = evalValues[idx] + '%';
    }, 2500);
}

// ============================
// TERMINAL AUTO-RESTART
// ============================
function initTerminal() {
    const lines = document.querySelectorAll('.term-line');
    const cursor = document.querySelector('.term-cursor');

    function restartAnimation() {
        lines.forEach(line => {
            line.style.animation = 'none';
            line.style.opacity = '0';
        });
        if (cursor) {
            cursor.style.animation = 'none';
            cursor.style.opacity = '0';
        }

        // Force reflow
        void document.querySelector('.terminal-body').offsetWidth;

        // Restart
        const delays = [0, 0.4, 0.9, 1.4, 2.0, 2.5, 3.2];
        lines.forEach((line, i) => {
            line.style.animation = `type-line 0.3s ease ${delays[i]}s forwards`;
        });

        if (cursor) {
            cursor.style.animation = `cursor-blink 1s step-end 3.8s infinite, fade-in-cursor 0.1s ease 3.8s forwards`;
        }
    }

    // Restart terminal every 8 seconds
    setInterval(restartAnimation, 8000);
}

// ============================
// ACTIVE NAV LINK HIGHLIGHT
// ============================
function initActiveNavLinks() {
    const sections = document.querySelectorAll('section[id]');
    const links = document.querySelectorAll('.nav-link');

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const id = entry.target.getAttribute('id');
                links.forEach(link => {
                    link.style.color = link.getAttribute('href') === `#${id}`
                        ? 'var(--text-primary)'
                        : '';
                });
            }
        });
    }, { rootMargin: '-40% 0px -40% 0px' });

    sections.forEach(section => observer.observe(section));
}

// ============================
// PARALLAX CHESS PIECES
// ============================
function initParallax() {
    const pieces = document.querySelectorAll('.chess-piece');
    let ticking = false;

    window.addEventListener('scroll', () => {
        if (!ticking) {
            requestAnimationFrame(() => {
                const scrollY = window.scrollY;
                pieces.forEach((piece, i) => {
                    const speed = 0.05 + (i % 3) * 0.03;
                    piece.style.transform = `translateY(${scrollY * speed}px)`;
                });
                ticking = false;
            });
            ticking = true;
        }
    }, { passive: true });
}

// ============================
// COUNTER ANIMATIONS
// ============================
function animateCounter(el, start, end, duration, suffix = '') {
    let startTime = null;

    function step(timestamp) {
        if (!startTime) startTime = timestamp;
        const elapsed = timestamp - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.floor(start + (end - start) * eased);
        el.textContent = current + suffix;
        if (progress < 1) requestAnimationFrame(step);
    }

    requestAnimationFrame(step);
}

function initCounters() {
    const statNumbers = document.querySelectorAll('.stat-number');
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const el = entry.target;
                const text = el.textContent;
                if (text === '50+') animateCounter(el, 0, 50, 1200, '+');
                else if (text === '10') animateCounter(el, 0, 10, 800);
                observer.unobserve(el);
            }
        });
    }, { threshold: 0.5 });

    statNumbers.forEach(el => observer.observe(el));
}

// ============================
// ADD HOVER GLOW TO FEATURE CARDS
// ============================
function initCardGlow() {
    document.querySelectorAll('.feature-card').forEach(card => {
        card.addEventListener('mousemove', function (e) {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            card.style.setProperty('--mouse-x', x + 'px');
            card.style.setProperty('--mouse-y', y + 'px');
        });
    });
}

// ============================
// CSS ADDITION: MOUSETRACK GLOW
// ============================
function injectMouseGlowCSS() {
    const style = document.createElement('style');
    style.textContent = `
        .feature-card::after {
            content: '';
            position: absolute;
            inset: 0;
            border-radius: inherit;
            background: radial-gradient(
                180px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
                rgba(124, 58, 237, 0.06),
                transparent 100%
            );
            pointer-events: none;
            opacity: 0;
            transition: opacity 0.3s ease;
        }
        .feature-card:hover::after {
            opacity: 1;
        }
        @keyframes fadeDown {
            from { opacity: 0; transform: translateY(-8px); }
            to { opacity: 1; transform: translateY(0); }
        }
    `;
    document.head.appendChild(style);
}

// ============================
// INIT ALL
// ============================
document.addEventListener('DOMContentLoaded', function () {
    initScrollReveal();
    initEngineOptions();
    animateMockup();
    initTerminal();
    initActiveNavLinks();
    initParallax();
    initCounters();
    initCardGlow();
    injectMouseGlowCSS();
});
